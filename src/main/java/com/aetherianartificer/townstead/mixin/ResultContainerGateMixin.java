package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.switchboard.ContentGates;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Crafting cannot produce an item whose system is switched off in this world: the result slot stays
 * empty. Recipes are loaded before a world's settings are known, so the check sits at the result.
 */
@Mixin(ResultContainer.class)
public abstract class ResultContainerGateMixin {

    //? if neoforge {
    @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    //?} else {
    /*@ModifyVariable(method = "m_6836_", remap = false, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    *///?}
    private ItemStack townstead$gateResult(ItemStack stack) {
        return ContentGates.enabled(stack) ? stack : ItemStack.EMPTY;
    }
}
