package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.SpeciesRigLayer;
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
 * Adds the {@link SpeciesRigLayer} to the vanilla player renderer (mirroring
 * {@code PlayerAttachmentLayerMixin}), so a player rendered as an MCA villager (the genetics
 * renderer) whose origin declares an alternate rig draws that model. Without this the player's
 * MCA body is suppressed by {@code VillagerBodyLayerSuppressMixin} with nothing to replace it.
 * No-op for default ({@code mca:villager}) rigs.
 */
@Mixin(PlayerRenderer.class)
public abstract class PlayerSpeciesRigLayerMixin
        extends LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public PlayerSpeciesRigLayerMixin(EntityRendererProvider.Context ctx,
                                      PlayerModel<AbstractClientPlayer> model, float shadowRadius) {
        super(ctx, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V", at = @At("TAIL"))
    private void townstead$addRigLayer(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        this.addLayer(new SpeciesRigLayer<>(this, RigModels.PLAYER_HOST_BASELINE, false));
    }
}
