package com.aetherianartificer.townstead.mixin.client;

import net.conczin.mca.MCAClient;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives back the body of a player drawn by another mod's own renderer, such as a Werewolves beast.
 * MCA hides the body on every living renderer for a player in its Villager model mode, because its
 * villager layers replace the vanilla player body. A renderer that is not the player renderer has no
 * such layers, so it drew nothing but its overlays (a werewolf's eyes). Here such a renderer gets the
 * render type vanilla would have given it. Priority after MCA's, so this sees its answer.
 */
@Mixin(value = LivingEntityRenderer.class, priority = 1500)
public abstract class ForeignPlayerRendererBodyMixin<T extends LivingEntity> extends EntityRenderer<T> {

    protected ForeignPlayerRendererBodyMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    //? if neoforge {
    @Inject(method = "getRenderType", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7225_", at = @At("RETURN"), cancellable = true)
    *///?}
    private void townstead$restoreForeignBody(T entity, boolean bodyVisible, boolean translucent,
                                               boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        if (cir.getReturnValue() != null || !(entity instanceof Player)) return;
        if ((Object) this instanceof PlayerRenderer || !MCAClient.useVillagerRenderer(entity.getUUID())) return;
        ResourceLocation texture = getTextureLocation(entity);
        @SuppressWarnings("unchecked")
        LivingEntityRenderer<T, ?> self = (LivingEntityRenderer<T, ?>) (Object) this;
        if (translucent) cir.setReturnValue(RenderType.itemEntityTranslucentCull(texture));
        else if (bodyVisible) cir.setReturnValue(self.getModel().renderType(texture));
        else if (glowing) cir.setReturnValue(RenderType.outline(texture));
    }
}
