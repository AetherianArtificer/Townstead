package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.FoodSafety;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Belt-and-suspenders backstop: when a villager is about to consume a food
 * item via LivingEntity.eat (the vanilla finishUsingItem → eat chain), and
 * FoodSafety says the item is unsafe (blacklist or harmful effects), skip
 * the eat entirely — stack is returned unchanged, no effects applied, no
 * nutrition awarded.
 *
 * Our SeekFoodTask already filters at the pick stage, but this covers any
 * third-party mod or MCA interaction that spins up the item-use flow on a
 * villager with an unsafe food. Target class is LivingEntity so the inject
 * fires for all LivingEntity.eat calls; we guard internally on villager
 * type to avoid touching players/mobs that aren't our responsibility.
 */
@Mixin(LivingEntity.class)
public abstract class VillagerEatSafetyMixin {

    //? if neoforge {
    @Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "eat(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/food/FoodProperties;)Lnet/minecraft/world/item/ItemStack;", remap = false, require = 0, at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$blockUnsafeFoodForVillagers(
            Level level, ItemStack stack, FoodProperties food,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof Villager)) return;
        if (FoodSafety.isSafeToEat(stack)) return;
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
    }
}
