package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.InvisFade;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCAClient;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fades the VANILLA body render path through invisibility transitions, for exactly the
 * players MCA's villager layers do not draw ({@code !MCAClient.useVillagerRenderer}) —
 * PLAYER/VANILLA model-mode players whose body is the plain player model.
 * {@code VillagerLayerFadeMixin} covers the MCA-layer path and {@code SpeciesRigLayer}
 * the alternate-rig path, so together the three cover every body an invisibility flip
 * can hide. While {@link InvisFade} is mid-fade this forces the translucent render type
 * (vanilla would return null and hard-hide) and scales the body draw's alpha. Extends
 * {@link EntityRenderer} to reach {@code getTextureLocation} (@Shadow does not traverse
 * the hierarchy).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class PlayerBodyFadeMixin<T extends LivingEntity, M extends EntityModel<T>>
        extends EntityRenderer<T> {

    protected PlayerBodyFadeMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    /** This render pass's fade; -1 = not a vanilla-faded entity, leave everything alone. */
    @Unique
    private static final ThreadLocal<Float> townstead$fade = ThreadLocal.withInitial(() -> -1f);

    //? if neoforge {
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    *///?}
    private void townstead$captureFade(T entity, float entityYaw, float partialTick, PoseStack pose,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        float fade = -1f;
        if (entity instanceof Player && !MCAClient.useVillagerRenderer(entity.getUUID())) {
            fade = InvisFade.alpha(entity, partialTick);
        }
        townstead$fade.set(fade);
    }

    //? if neoforge {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_7225_", at = @At("HEAD"), cancellable = true)
    *///?}
    private void townstead$fadeRenderType(T entity, boolean bodyVisible, boolean translucent,
                                          boolean glowing, CallbackInfoReturnable<RenderType> cir) {
        float fade = townstead$fade.get();
        if (fade > 0f && fade < 1f && !glowing) {
            cir.setReturnValue(RenderType.entityTranslucent(getTextureLocation(entity)));
        }
    }

    //? if neoforge {
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"),
            index = 4, expect = 1)
    private int townstead$fadeBodyAlpha(int color) {
        float fade = townstead$fade.get();
        if (fade < 0f || fade >= 1f) return color;
        int alpha = (color >>> 24) & 0xFF;
        return (Math.round(alpha * fade) << 24) | (color & 0xFFFFFF);
    }
    //?} else {
    /*@ModifyArg(method = "m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/model/EntityModel;m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"),
            index = 7, expect = 1)
    private float townstead$fadeBodyAlpha(float alpha) {
        float fade = townstead$fade.get();
        return fade < 0f || fade >= 1f ? alpha : alpha * fade;
    }
    *///?}
}
