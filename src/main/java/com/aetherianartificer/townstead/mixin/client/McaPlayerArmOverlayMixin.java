package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.HandFade;
import com.aetherianartificer.townstead.client.species.SkinOverlayLayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA 7.6 renders and then cancels first-person hands inside a private method merged into
 * PlayerRenderer. Hook that exact pass so Townstead receives MCA's already-posed skin arm, and fade
 * the hand like the third-person body.
 *
 * <p>1.20.1 Forge SRG: {@code m_110458_} the cutout render type, {@code m_104306_} the coloured part render.</p>
 */
@Mixin(value = PlayerRenderer.class, priority = 900)
public abstract class McaPlayerArmOverlayMixin {

    @Inject(method = "mca$renderCustomArm", at = @At("HEAD"), remap = false, expect = 1)
    private void townstead$beginFade(PoseStack pose, MultiBufferSource buffers, int light,
                                     AbstractClientPlayer player, ModelPart arm,
                                     ModelPart sleeve, VillagerLayer<?, ?> layer,
                                     CallbackInfo ci) {
        HandFade.begin(player);
    }

    @WrapOperation(method = "mca$renderCustomArm", remap = false, expect = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;m_110458_(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;",
                    remap = false))
    private RenderType townstead$fadeArmType(ResourceLocation texture, Operation<RenderType> original) {
        return HandFade.type(original.call(texture), texture);
    }

    @ModifyArg(method = "mca$renderCustomArm", remap = false, expect = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;m_104306_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                    remap = false),
            index = 7)
    private float townstead$fadeArmAlpha(float alpha) {
        return alpha * HandFade.current();
    }

    @Inject(method = "mca$renderCustomArm", at = @At("RETURN"), remap = false, expect = 1)
    private void townstead$overlayMcaSkinArm(PoseStack pose, MultiBufferSource buffers, int light,
                                             AbstractClientPlayer player, ModelPart arm,
                                             ModelPart sleeve, VillagerLayer<?, ?> layer,
                                             CallbackInfo ci) {
        HandFade.end();
        if (layer instanceof SkinLayer<?, ?>) {
            SkinOverlayLayer.renderFirstPersonPart(pose, buffers, light, player, arm);
        }
    }
}
