package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.charter.CharterLecternRenderer;
import com.aetherianartificer.townstead.politics.charter.CharterLecternAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.LecternRenderer;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the charter book and its cloth without putting either item into the lectern. Injects at every
 * return, since the renderer leaves early when the lectern holds no book.
 */
@Mixin(LecternRenderer.class)
public abstract class LecternCharterRendererMixin {
    //? if neoforge {
    @Inject(method = "render", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "m_6922_(Lnet/minecraft/world/level/block/entity/LecternBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V", remap = false, at = @At("RETURN"))
    *///?}
    private void townstead$renderCharter(LecternBlockEntity lectern, float partialTick, PoseStack pose,
                                          MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        if (!(lectern instanceof CharterLecternAccess access) || access.townstead$charterState() == CharterLecternAccess.NONE
                || lectern.getBlockState().getValue(LecternBlock.HAS_BOOK)) return;

        CharterLecternRenderer.render(lectern, access, pose, buffers, light, overlay);
    }
}
