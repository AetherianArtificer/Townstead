package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.emote.EmoteBodyTransformSampler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the active emote's body bone translation and rotation at the
 * entity-render level, so MCA villagers visibly translate during emotes that
 * use {@code torso} (renamed to {@code body} by Emotecraft for old emotes)
 * position keyframes — the same way Emotecraft's
 * {@code PlayerRendererMixin.applyBodyTransforms} lifts the player.
 *
 * <p>Skipped on {@link Player} entities because Emotecraft already handles
 * those natively via its own {@code PlayerRenderer} mixin; doubling up would
 * lift the player twice as high as authored.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererEmoteMixin {
    //? if neoforge {
    @Inject(method = "setupRotations", at = @At("RETURN"))
    private void townstead$applyEmoteEntityTransform(
            LivingEntity entity,
            PoseStack poseStack,
            float ageInTicks,
            float bodyYaw,
            float partialTick,
            float scale,
            CallbackInfo ci
    ) {
        if (entity instanceof Player) return;
        EmoteBodyTransformSampler.apply(entity, poseStack, partialTick);
    }
    //?} else {
    /*@Inject(method = "m_7523_", remap = false, at = @At("RETURN"), require = 0)
    private void townstead$applyEmoteEntityTransform(
            LivingEntity entity,
            PoseStack poseStack,
            float ageInTicks,
            float bodyYaw,
            float partialTick,
            CallbackInfo ci
    ) {
        if (entity instanceof Player) return;
        EmoteBodyTransformSampler.apply(entity, poseStack, partialTick);
    }
    *///?}
}
