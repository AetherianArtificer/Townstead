package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.recipe.GatedRecipes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gates {@code recipe} genes: a player can't take a crafting result whose output is gated
 * unless they have the granting gene. Hooks {@code Slot.mayPickup} ({@code m_8010_} on the
 * 1.20.1 Forge build) and limits to the crafting {@link ResultSlot}.
 */
@Mixin(Slot.class)
public abstract class SlotResultGateMixin {

    //? if neoforge {
    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_8010_", at = @At("HEAD"), cancellable = true, remap = false)
    *///?}
    private void townstead$gateRecipe(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResultSlot)) return;
        ItemStack result = ((Slot) (Object) this).getItem();
        if (result.isEmpty()) return;
        if (GatedRecipes.isGated(result.getItem()) && !GatedRecipes.canCraft(player, result.getItem())) {
            cir.setReturnValue(false);
        }
    }
}
