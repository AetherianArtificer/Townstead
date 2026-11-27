package com.aetherianartificer.townstead.mixin.compat.cybernetics;

import net.minecraft.client.renderer.ItemInHandRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if neoforge {
import com.aetherianartificer.townstead.compat.cybernetics.FaceplateDisguiseClient;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/** First-person hands draw as the player a worn alias names, as Cybernetics' own disguise skin does. */
@Mixin(ItemInHandRenderer.class)
public abstract class FaceplateDisguiseHandMixin {

    //? if neoforge {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void townstead$beginDisguise(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                         LocalPlayer player, int light, CallbackInfo ci) {
        FaceplateDisguiseClient.beginRender(player);
    }

    @Inject(method = "renderHandsWithItems", at = @At("RETURN"))
    private void townstead$endDisguise(float partialTick, PoseStack pose, MultiBufferSource.BufferSource buffers,
                                       LocalPlayer player, int light, CallbackInfo ci) {
        FaceplateDisguiseClient.endRender();
    }
    //?}
}
