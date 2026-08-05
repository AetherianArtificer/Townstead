package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.attachment.AttachmentRenderLayer;
import com.aetherianartificer.townstead.client.species.WornItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the cosmetic {@link AttachmentRenderLayer} to the vanilla player renderer
 * (both default and slim), mirroring how MCA augments the same constructor. The
 * layer no-ops for players with no expressed attachment genes, and anchors to
 * whichever model is active that frame (MCA swaps in its villager model for
 * genetics players), so attachments follow the player either way.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerAttachmentLayerMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerAttachmentLayerMixin(EntityRendererProvider.Context ctx,
                                      PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void townstead$addAttachmentLayer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        this.addLayer(new AttachmentRenderLayer<>(this));
        this.addLayer(new WornItemLayer<>(this));
    }

    @Unique
    private boolean townstead$overlayInserted;

    /**
     * Inserts the skin-overlay layer before MCA's FaceLayer on the PLAYER renderer, so
     * villager-model players paint overlays under their eyes too. Lazy on the first
     * {@code scale} call: MCA adds its player layers in a constructor-TAIL mixin, so a
     * constructor-time scan could run before they exist. When MCA's player rendering is
     * absent (no FaceLayer), no layer is added at all.
     */
    //? if neoforge {
    @Inject(method = "scale(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_7546_(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;F)V",
            at = @At("HEAD"), remap = false, require = 1)
    *///?}
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void townstead$insertSkinOverlay(AbstractClientPlayer player, PoseStack matrices, float f,
                                             CallbackInfo ci) {
        if (townstead$overlayInserted) return;
        townstead$overlayInserted = true;
        for (int i = 0; i < this.layers.size(); i++) {
            if (this.layers.get(i) instanceof net.conczin.mca.client.render.layer.FaceLayer) {
                var overlayModel = new net.conczin.mca.client.model.PlayerEntityExtendedModel<AbstractClientPlayer>(
                        net.minecraft.client.model.geom.builders.LayerDefinition.create(
                                net.conczin.mca.client.model.VillagerEntityModelMCA.bodyData(
                                        //? if neoforge {
                                        new net.minecraft.client.model.geom.builders.CubeDeformation(0.005f)),
                                        //?} else {
                                        /*new net.minecraft.client.model.geom.builders.CubeDeformation(0.001f)),
                                        *///?}
                                64, 64).bakeRoot());
                RenderLayer layer = new com.aetherianartificer.townstead.client.species.SkinOverlayLayer(
                        this, overlayModel);
                this.layers.add(i, layer);
                com.aetherianartificer.townstead.Townstead.LOGGER.info(
                        "Skin overlay layer inserted at {} (before FaceLayer) of {} on the player renderer",
                        i, this.layers.size());
                return;
            }
        }
    }
}
