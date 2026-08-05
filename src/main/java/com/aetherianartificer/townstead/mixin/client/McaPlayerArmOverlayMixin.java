package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.SkinOverlayLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.SkinLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MCA 7.6 renders and then cancels first-person hands inside a private method merged into
 * PlayerRenderer. Hook that exact pass so Townstead receives MCA's already-posed skin arm.
 */
@Mixin(value = PlayerRenderer.class, priority = 900)
public abstract class McaPlayerArmOverlayMixin {

    @Inject(method = "mca$renderCustomArm", at = @At("RETURN"), remap = false, require = 1)
    private void townstead$overlayMcaSkinArm(PoseStack pose, MultiBufferSource buffers, int light,
                                             AbstractClientPlayer player, ModelPart arm,
                                             ModelPart sleeve, VillagerLayer<?, ?> layer,
                                             CallbackInfo ci) {
        if (layer instanceof SkinLayer<?, ?>) {
            SkinOverlayLayer.renderFirstPersonPart(pose, buffers, light, player, arm);
        }
    }
}
