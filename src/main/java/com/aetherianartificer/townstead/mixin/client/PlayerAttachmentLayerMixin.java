package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.attachment.AttachmentRenderLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
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
    }
}
