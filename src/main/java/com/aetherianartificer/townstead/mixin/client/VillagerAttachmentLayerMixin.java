package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.attachment.AttachmentRenderLayer;
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
        this.addLayer(new AttachmentRenderLayer<>(this));
    }
}
