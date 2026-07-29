package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.SpeciesRigLayer;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a non-humanoid rig's authored {@code poses.crawl} body lean while it is in the SWIMMING pose
 * (crawling under a low ceiling, or sprint-swimming).
 *
 * <p>Vanilla's own -90 degree pitch for that pose is suppressed for these rigs by
 * {@link LivingEntitySwimClimbMixin}, so without a lean they simply stay upright. This adds back whatever
 * the pack authored, in the SAME entity-root frame {@code poses.sleep}'s body transform uses, so
 * {@code yaw}/{@code pitch}/{@code roll} read identically across both states. TAIL, not HEAD-cancel: the
 * normal body-yaw rotation still applies, we only add to it.</p>
 *
 * <p>Eased by the factor {@link SpeciesRigLayer} keeps for the state, so the lean fades in and out with
 * the pose's bones and offset rather than snapping. Like vanilla's own swim pitch, a lean here also turns
 * the renderer's grounding translate that follows, which is what keeps the body seated; a pack corrects
 * any residual with {@code crawl.body.offset}.</p>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererCrawlMixin {

    //? if neoforge {
    @Inject(method = "setupRotations", at = @At("TAIL"))
    private void townstead$crawlLean(
            LivingEntity entity, PoseStack poseStack, float ageInTicks, float bodyYaw,
            float partialTick, float scale, CallbackInfo ci) {
        townstead$applyCrawlLean(entity, poseStack);
    }
    //?} else {
    /*@Inject(method = "m_7523_", remap = false, at = @At("TAIL"), require = 0)
    private void townstead$crawlLean(
            LivingEntity entity, PoseStack poseStack, float ageInTicks, float bodyYaw,
            float partialTick, CallbackInfo ci) {
        townstead$applyCrawlLean(entity, poseStack);
    }
    *///?}

    @Unique
    private static void townstead$applyCrawlLean(LivingEntity entity, PoseStack poseStack) {
        if (!entity.isVisuallySwimming()) return;
        String rigBase = RigModels.rigBaseFor(entity);
        if (!RigModels.isGeneric(rigBase)) return;
        RigDefinition.BodyPose crawl = RigModels.crawlPose(rigBase);
        if (crawl == null) return;
        float f = SpeciesRigLayer.poseFactor(entity.getId(), "crawl");
        if (f <= 0f) return;
        if (crawl.yaw() != 0f) poseStack.mulPose(Axis.YP.rotationDegrees(crawl.yaw() * f));
        if (crawl.pitch() != 0f) poseStack.mulPose(Axis.XP.rotationDegrees(crawl.pitch() * f));
        if (crawl.roll() != 0f) poseStack.mulPose(Axis.ZP.rotationDegrees(crawl.roll() * f));
    }
}
