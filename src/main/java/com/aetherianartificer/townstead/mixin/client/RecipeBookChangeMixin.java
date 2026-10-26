package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.item.LearnedRecipes;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells recipe viewers the local recipe book changed, so learned-recipe items can appear. */
@Mixin(ClientPacketListener.class)
public abstract class RecipeBookChangeMixin {

    //? if neoforge {
    @Inject(method = "handleAddOrRemoveRecipes", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_8076_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$recipesChanged(ClientboundRecipePacket packet, CallbackInfo ci) {
        LearnedRecipes.changed();
    }
}
