package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.HandFade;
import com.aetherianartificer.townstead.client.species.SkinOverlayLayer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.model.PlayerModel;
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
 * Newer MCA draws the first-person hand on each layer's own visible model, posed from a separate
 * animation arm with different geometry. Fade that hand like the third-person body, and paint skin
 * overlays on the visible skin arm, so they sit on the hand MCA shows instead of floating on the
 * renderer's animation arm. The fade itself is opened by {@link PlayerHandFadeMixin} around the
 * whole hand pass.
 */
@Mixin(value = PlayerRenderer.class, priority = 1100)
public abstract class McaPlayerArmLayerOverlayMixin {

    @WrapOperation(method = "mca$renderArmLayer", remap = false, require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType;entityCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private RenderType townstead$fadeArmType(ResourceLocation texture, Operation<RenderType> original) {
        return HandFade.type(original.call(texture), texture);
    }

    @ModifyArg(method = "mca$renderArmLayer", remap = false, require = 1,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4)
    private int townstead$fadeArmColor(int color) {
        return HandFade.color(color, HandFade.current());
    }

    @Inject(method = "mca$renderArmLayer", at = @At("RETURN"), remap = false, require = 1)
    private void townstead$overlayMcaSkinArm(PoseStack pose, MultiBufferSource buffers, int light,
                                             AbstractClientPlayer player,
                                             VillagerLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer,
                                             boolean right, ModelPart sourceArm, CallbackInfo ci) {
        if (!(layer instanceof SkinLayer<?, ?>)) return;
        ModelPart arm = right ? layer.model.rightArm : layer.model.leftArm;
        arm.copyFrom(sourceArm);
        SkinOverlayLayer.renderFirstPersonPart(pose, buffers, light, player, arm);
        SkinOverlayLayer.markFirstPersonArmPainted();
    }
}
