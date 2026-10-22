package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.HandFade;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fades the first-person hand like the third-person body ({@code pheno:opacity}, invisibility
 * fades). Opens {@link HandFade} around the whole hand pass, so MCA's per-layer arm hooks read it
 * too, and fades the plain player arm and sleeve drawn when MCA does not draw its own.
 *
 * <p>1.20.1 Forge SRG: {@code m_117775_} renderHand, {@code m_110446_} entitySolid,
 * {@code m_104301_} the uncoloured part render.</p>
 */
@Mixin(value = PlayerRenderer.class, priority = 1100)
public abstract class PlayerHandFadeMixin {

    //? if neoforge {
    @Inject(method = "renderHand", at = @At("HEAD"), require = 1)
    //?} else {
    /*@Inject(method = "m_117775_", at = @At("HEAD"), remap = false, require = 1)
    *///?}
    private void townstead$beginFade(PoseStack pose, MultiBufferSource buffers, int light,
                                     AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
                                     CallbackInfo ci) {
        HandFade.begin(player);
    }

    //? if neoforge {
    @Inject(method = "renderHand", at = @At("RETURN"), require = 1)
    //?} else {
    /*@Inject(method = "m_117775_", at = @At("RETURN"), remap = false, require = 1)
    *///?}
    private void townstead$endFade(PoseStack pose, MultiBufferSource buffers, int light,
                                   AbstractClientPlayer player, ModelPart arm, ModelPart sleeve,
                                   CallbackInfo ci) {
        HandFade.end();
    }

    //? if neoforge {
    @WrapOperation(method = "renderHand", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entitySolid(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    //?} else {
    /*@WrapOperation(method = "m_117775_", remap = false, require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;m_110446_(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
                    remap = false))
    *///?}
    private RenderType townstead$fadeArmType(ResourceLocation texture, Operation<RenderType> original) {
        return HandFade.type(original.call(texture), texture);
    }

    //? if neoforge {
    @WrapOperation(method = "renderHand", require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V"))
    //?} else {
    /*@WrapOperation(method = "m_117775_", remap = false, require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;m_104301_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;II)V",
                    remap = false))
    *///?}
    private void townstead$fadeArmBuffer(ModelPart part, PoseStack pose, VertexConsumer buffer, int light,
                                         int overlay, Operation<Void> original) {
        original.call(part, pose, HandFade.wrap(buffer), light, overlay);
    }
}
