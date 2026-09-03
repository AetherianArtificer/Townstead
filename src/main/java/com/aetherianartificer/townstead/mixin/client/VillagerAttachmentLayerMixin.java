package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.attachment.AttachmentRenderLayer;
import com.aetherianartificer.townstead.client.species.WornItemLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerLikeEntityMCARenderer;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the cosmetic {@link AttachmentRenderLayer} to MCA's villager renderer. Done
 * lazily on the first {@code scale} call (an MCA method, so its name survives both
 * branches) rather than the constructor, which avoids a vanilla-type descriptor on
 * the 1.20.1 Forge refmap. {@code scale} runs in the render setup before the layer
 * loop, so adding the layer there takes effect the same frame and only once.
 */
@Mixin(VillagerLikeEntityMCARenderer.class)
public abstract class VillagerAttachmentLayerMixin<T extends Mob & VillagerLike<T>>
        extends LivingEntityRenderer<T, VillagerEntityModelMCA<T>> {

    @Unique
    private boolean townstead$attachmentLayerAdded;

    protected VillagerAttachmentLayerMixin(EntityRendererProvider.Context ctx,
                                           VillagerEntityModelMCA<T> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "scale", at = @At("HEAD"), remap = false, require = 1)
    private void townstead$addAttachmentLayer(Mob villager, PoseStack matrices, float tickDelta, CallbackInfo ci) {
        if (townstead$attachmentLayerAdded) return;
        townstead$attachmentLayerAdded = true;
        // First in the stack, so every layer that copies MCA's model pose sees adult proportions.
        this.layers.add(0, new com.aetherianartificer.townstead.client.species.AdultProportionsLayer<>(this));
        this.addLayer(new AttachmentRenderLayer<>(this));
        this.addLayer(new WornItemLayer<>(this));
        com.aetherianartificer.townstead.compat.curios.CuriosClientCompat.addLayer(this);
        com.aetherianartificer.townstead.compat.curios.GraftedWearableLayers.addAll(this);
        townstead$insertSkinOverlayLayer();
    }

    /**
     * Inserts the {@link com.aetherianartificer.townstead.client.species.SkinOverlayLayer}
     * into MCA's layer stack directly BEFORE its FaceLayer, so overlays paint on the skin
     * but under the eyes, clothing, and hair. The layer gets its own body shell dilated
     * 0.005, between MCA's skin (0) and face (0.01) shells, so nothing z-fights.
     */
    @Unique
    private void townstead$insertSkinOverlayLayer() {
        VillagerEntityModelMCA<T> overlayModel = new VillagerEntityModelMCA<>(
                net.minecraft.client.model.geom.builders.LayerDefinition.create(
                        VillagerEntityModelMCA.bodyData(
                                new net.minecraft.client.model.geom.builders.CubeDeformation(0.005f)),
                        64, 64).bakeRoot());
        var layer = new com.aetherianartificer.townstead.client.species.SkinOverlayLayer<>(this, overlayModel);
        for (int i = 0; i < this.layers.size(); i++) {
            if (this.layers.get(i) instanceof net.conczin.mca.client.render.layer.FaceLayer) {
                this.layers.add(i, layer);
                com.aetherianartificer.townstead.Townstead.LOGGER.info(
                        "Skin overlay layer inserted at {} (before FaceLayer) of {}", i, this.layers.size());
                return;
            }
        }
        this.addLayer(layer);   // no face layer found: painting over the skin still beats not painting
        com.aetherianartificer.townstead.Townstead.LOGGER.warn(
                "Skin overlay layer APPENDED (no FaceLayer found among {} layers)", this.layers.size());
    }
}
