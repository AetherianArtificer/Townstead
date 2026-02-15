package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared eating flow for villagers using vanilla item use timing/animation.
 * This gives the same duration and bite effects as players for each food item.
 */
public final class VillagerEatingManager {

    private record PendingEat(ItemStack food, ItemStack previousMainHand, long finishTick) {}

    private static final Map<Integer, PendingEat> PENDING = new ConcurrentHashMap<>();

    private VillagerEatingManager() {}

    public static boolean isEating(VillagerEntityMCA villager) {
        return PENDING.containsKey(villager.getId()) || villager.isUsingItem();
    }

    public static boolean startEating(VillagerEntityMCA villager, ItemStack foodStack) {
        if (foodStack.isEmpty() || isEating(villager)) return false;
        FoodProperties food = foodStack.get(DataComponents.FOOD);
        if (food == null) return false;

        ItemStack oneBite = foodStack.copyWithCount(1);
        ItemStack previousMainHand = villager.getMainHandItem().copy();
        int useDuration = oneBite.getUseDuration(villager);
        if (useDuration <= 0) useDuration = 32;

        PENDING.put(villager.getId(),
                new PendingEat(oneBite.copy(), previousMainHand, villager.level().getGameTime() + useDuration));

        villager.setItemInHand(InteractionHand.MAIN_HAND, oneBite);
        villager.startUsingItem(InteractionHand.MAIN_HAND);
        return true;
    }

    /**
     * Finalizes a pending eat action when vanilla item use ends.
     * Returns true if hunger value changed.
     */
    public static boolean tickAndFinalize(VillagerEntityMCA villager, CompoundTag hungerTag) {
        PendingEat pending = PENDING.get(villager.getId());
        if (pending == null) return false;
        if (villager.isUsingItem()) return false;

        boolean completed = villager.level().getGameTime() >= pending.finishTick();
        if (!completed) {
            // If item use was interrupted early, restart it to avoid visual flicker and
            // to preserve vanilla-like "hold to finish eating" behavior.
            villager.setItemInHand(InteractionHand.MAIN_HAND, pending.food().copy());
            villager.startUsingItem(InteractionHand.MAIN_HAND);
            return false;
        }

        PENDING.remove(villager.getId());
        villager.setItemInHand(InteractionHand.MAIN_HAND, pending.previousMainHand().copy());

        FoodProperties food = pending.food().get(DataComponents.FOOD);
        if (food == null) return false;

        int before = HungerData.getHunger(hungerTag);
        HungerData.applyFood(hungerTag, food);
        HungerData.setLastAteTime(hungerTag, villager.level().getGameTime());
        return HungerData.getHunger(hungerTag) != before;
    }
}
