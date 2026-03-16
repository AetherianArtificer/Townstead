package com.aetherianartificer.townstead.thirst;

import com.aetherianartificer.townstead.compat.mca.McaSicknessAdapter;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VillagerDrinkingManager {

    private record PendingDrink(ItemStack drink, ItemStack previousMainHand, long finishTick, boolean shouldDropBottle) {}

    private static final Map<Integer, PendingDrink> PENDING = new ConcurrentHashMap<>();

    private VillagerDrinkingManager() {}

    public static boolean isDrinking(VillagerEntityMCA villager) {
        return PENDING.containsKey(villager.getId()) || villager.isUsingItem();
    }

    public static boolean startDrinking(VillagerEntityMCA villager, ItemStack drinkStack) {
        if (drinkStack.isEmpty() || isDrinking(villager)) return false;
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !bridge.itemRestoresThirst(drinkStack)) return false;

        //? if >=1.21 {
        ItemStack oneSip = drinkStack.copyWithCount(1);
        //?} else {
        /*ItemStack oneSip = drinkStack.copy(); oneSip.setCount(1);
        *///?}
        ItemStack previousMainHand = villager.getMainHandItem().copy();
        //? if >=1.21 {
        int useDuration = oneSip.getUseDuration(villager);
        //?} else {
        /*int useDuration = oneSip.getUseDuration();
        *///?}
        if (useDuration <= 0) useDuration = 32;

        boolean shouldDropBottle = bridge.isDrink(oneSip) && oneSip.is(Items.POTION);

        PENDING.put(villager.getId(),
                new PendingDrink(oneSip.copy(), previousMainHand, villager.level().getGameTime() + useDuration, shouldDropBottle));

        villager.setItemInHand(InteractionHand.MAIN_HAND, oneSip);
        villager.startUsingItem(InteractionHand.MAIN_HAND);
        return true;
    }

    public static boolean tickAndFinalize(VillagerEntityMCA villager, net.minecraft.nbt.CompoundTag thirstTag) {
        PendingDrink pending = PENDING.get(villager.getId());
        if (pending == null) return false;
        if (villager.isUsingItem()) return false;

        boolean completed = villager.level().getGameTime() >= pending.finishTick();
        if (!completed) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, pending.drink().copy());
            villager.startUsingItem(InteractionHand.MAIN_HAND);
            return false;
        }

        PENDING.remove(villager.getId());
        villager.setItemInHand(InteractionHand.MAIN_HAND, pending.previousMainHand().copy());

        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) return false;

        int beforeThirst = ThirstData.getThirst(thirstTag);
        int beforeQuenched = ThirstData.getQuenched(thirstTag);

        int hydration = bridge.hydration(pending.drink());
        int quenched = bridge.quenched(pending.drink());
        boolean isPurityWater = bridge.isPurityWaterContainer(pending.drink());
        ThirstCompatBridge.PurityResult purityResult = isPurityWater
                ? bridge.evaluatePurity(bridge.purity(pending.drink()), villager.getRandom())
                : new ThirstCompatBridge.PurityResult(true, false, false, -1);

        if (purityResult.sickness()) {
            villager.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 20 * 5, 0));
            villager.addEffect(new MobEffectInstance(MobEffects.HUNGER, 20 * 30, 0));
            McaSicknessAdapter.markSick(villager, false);
        }
        if (purityResult.poison()) {
            villager.addEffect(new MobEffectInstance(MobEffects.POISON, 20 * 10, 0));
            McaSicknessAdapter.markSick(villager, true);
        }

        if (purityResult.applyHydration()) {
            ThirstData.applyDrink(thirstTag, hydration, quenched, bridge.extraHydrationToQuenched());
        }

        if (pending.shouldDropBottle()) {
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
            ItemStack remainder = villager.getInventory().addItem(bottle);
            if (!remainder.isEmpty()) {
                ItemEntity drop = new ItemEntity(villager.level(), villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
                drop.setPickUpDelay(0);
                villager.level().addFreshEntity(drop);
            }
        }

        ThirstData.setLastDrankTime(thirstTag, villager.level().getGameTime());
        return ThirstData.getThirst(thirstTag) != beforeThirst || ThirstData.getQuenched(thirstTag) != beforeQuenched;
    }
}
