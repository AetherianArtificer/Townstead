package com.aetherianartificer.townstead.mixin.compat.pizzadelight;

import com.aetherianartificer.townstead.compat.pizzadelight.PizzaDelightCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Credits a player with pizza-making when they take an assembled pizza from Pizza Delight's
 * pizza station (Tiviacz1337's; the same-id Aidan mod has no such class, so this never
 * applies there). The slot class overrides vanilla {@code Slot.onTake}, so the 1.20.1 Forge
 * production jar carries the SRG name.
 */
@Pseudo
@Mixin(targets = "com.tiviacz.pizzadelight.container.slots.PizzaStationResultSlot")
public abstract class PizzaStationResultSlotMixin {

    @Inject(
            //? if >=1.21 {
            method = "onTake",
            //?} else {
            /*method = "m_142406_(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            *///?}
            at = @At("TAIL"), remap = false)
    private void townstead$creditPizzaAssembled(Player player, ItemStack stack, CallbackInfo ci) {
        if (player != null && !player.level().isClientSide) {
            PizzaDelightCompat.onPizzaAssembled(player, stack);
        }
    }
}
