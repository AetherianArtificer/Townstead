package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.FoodSafety;
import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
//? if neoforge {
import net.minecraft.world.food.FoodProperties;
//?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Villager hook on LivingEntity.eat (the vanilla finishUsingItem → eat chain),
 * doing two jobs at the moment any villager actually consumes food:
 *
 * <p><b>Safety backstop</b>: if FoodSafety says the item is unsafe (blacklist or
 * harmful effects), skip the eat entirely — stack is returned unchanged, no
 * effects applied, no nutrition awarded. Our SeekFoodTask already filters at the
 * pick stage; this covers any third-party mod or MCA interaction that spins up
 * the item-use flow on a villager with an unsafe food.
 *
 * <p><b>Unmanaged-eat crediting</b>: eats Townstead did not initiate — MCA
 * 7.6.28+/7.7.18+ recovery eating (hurt villagers snack from inventory to heal),
 * or another mod force-feeding — still fill the Townstead hunger ledger, so the
 * same item yields the same nutrition no matter who drove the bite. Credit runs
 * at HEAD (after the safety check passes) because eat() shrinks the stack, which
 * can read as empty by RETURN; the managed flow is excluded inside
 * {@link VillagerConsumptionManager#creditUnmanagedFood} (still pending here).
 *
 * <p>1.20.1's eat is the two-arg {@code m_5584_(Level, ItemStack)} (SRG, no
 * refmap); 1.21.1's is the three-arg mojmap overload carrying FoodProperties.
 */
@Mixin(LivingEntity.class)
public abstract class VillagerEatSafetyMixin {

    //? if neoforge {
    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    private void townstead$blockUnsafeFoodForVillagers(
            Level level, ItemStack stack, FoodProperties food,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        townstead$handleVillagerEat(stack, cir);
    }
    //?} else {
    /*@Inject(method = "m_5584_(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;", remap = false, require = 0, at = @At("HEAD"), cancellable = true)
    private void townstead$blockUnsafeFoodForVillagers(
            Level level, ItemStack stack,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        townstead$handleVillagerEat(stack, cir);
    }
    *///?}

    @Unique
    private void townstead$handleVillagerEat(ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Villager)) return;
        if (!FoodSafety.isSafeToEat(stack, self)) {
            // Unsafe food reached the eat stage — refuse. Return the unmodified
            // stack so the item stays in the villager's inventory (they'll keep
            // trying uselessly, but the stack isn't lost and they aren't hurt).
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                Townstead.LOGGER.info(
                        "[VillagerEat] blocked unsafe food {} for villager {}",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()),
                        self.getUUID());
            }
            self.stopUsingItem();
            cir.setReturnValue(stack);
            return;
        }
        if (self instanceof VillagerEntityMCA villager) {
            VillagerConsumptionManager.creditUnmanagedFood(villager, stack);
        }
    }
}
