package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.IrisTint;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.conczin.mca.client.resources.EyeTextureLayers;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An {@code iris} gene recolors MCA's own iris layer (every face keeps its shape, lashes and blink),
 * and a glowing one redraws that layer emissive after the face. MCA's methods, hence
 * {@code remap=false}; {@code require = 0} keeps older MCA builds without split eye layers inert.
 */
@Mixin(FaceLayer.class)
public abstract class FaceLayerIrisMixin<T extends LivingEntity, M extends HumanoidModel<T>> {

    @Shadow(remap = false)
    private ResourceLocation getOrGenerateEyeLayer(ResourceLocation original, EyeTextureLayers.Layer layer,
                                                   EyeTextureLayers.Side side) { throw new AssertionError(); }

    @Shadow(remap = false)
    private boolean isBlinking(T villager) { throw new AssertionError(); }

    @Inject(method = "getEyeColor", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$irisTint(T villager, float tickDelta, boolean left, CallbackInfoReturnable<Integer> cir) {
        IrisTint.Tint tint = IrisTint.of(villager);
        if (tint != null) cir.setReturnValue(0xFF000000 | tint.rgb());
    }

    @Inject(method = "renderFinal", at = @At("TAIL"), remap = false, require = 0)
    private void townstead$irisGlow(PoseStack transform, MultiBufferSource provider, int light, T villager,
                                    float tickDelta, boolean visible, boolean glowing, CallbackInfo ci) {
        if (!visible) return;
        IrisTint.Tint tint = IrisTint.of(villager);
        if (tint == null || !tint.glow() || isBlinking(villager)) return;
        @SuppressWarnings("unchecked")
        VillagerLayer<T, M> self = (VillagerLayer<T, M>) (Object) this;
        ResourceLocation iris = getOrGenerateEyeLayer(self.getSkin(villager),
                EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.FULL);
        if (iris == null) return;
        var buffer = provider.getBuffer(RenderType.eyes(iris));
        int color = tint.rgb();
        //? if neoforge {
        self.model.renderToBuffer(transform, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFF000000 | color);
        //?} else {
        /*self.model.renderToBuffer(transform, buffer, 0xF000F0, OverlayTexture.NO_OVERLAY,
                ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        *///?}
    }
}
