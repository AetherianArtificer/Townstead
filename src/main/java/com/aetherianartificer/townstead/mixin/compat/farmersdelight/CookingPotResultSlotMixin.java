package com.aetherianartificer.townstead.mixin.compat.farmersdelight;

import com.aetherianartificer.townstead.profession.career.PlayerWorkHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Credits a player with cooking when they take a meal from a Farmer's Delight cooking pot
 * (direct take and shift-click both route through the slot's onTake). The slot class overrides
 * vanilla {@code Slot.onTake}, so the 1.20.1 Forge production jar carries the SRG name.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.container.CookingPotResultSlot")
public abstract class CookingPotResultSlotMixin {

    @Inject(
            //? if >=1.21 {
            method = "onTake",
            //?} else {
            /*method = "m_142406_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            *///?}
            at = @At("TAIL"), remap = false)
    private void townstead$creditMealTaken(Player player, ItemStack stack, CallbackInfo ci) {
        if (player != null && !player.level().isClientSide) {
            PlayerWorkHooks.onCookingCompleted(
                    player, stack, stack.getCount(), "cooking_pot");
        }
    }
}
