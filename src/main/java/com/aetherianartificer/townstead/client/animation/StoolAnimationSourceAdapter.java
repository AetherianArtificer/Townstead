package com.aetherianartificer.townstead.client.animation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Reusable seated pose for tall stools. Vanilla's passenger pose rotates each entire leg forward;
 * player-animation-lib's bend channel lets the lower half hang naturally from the knee instead.
 */
public final class StoolAnimationSourceAdapter implements AnimationSourceAdapter {
    private static final TagKey<Block> STOOLS = TagKey.create(Registries.BLOCK,
            ResourceLocation.tryParse("townstead_hangouts:stools"));
    private static final AnimationTransform.Operation SET = AnimationTransform.Operation.SET;

    @Override
    public String id() {
        return "stool";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        LivingEntity entity = context.entity();
        Entity vehicle = entity.getVehicle();
        if (vehicle == null || !isAboveStool(entity, vehicle.blockPosition())) return List.of();
        var clip = com.aetherianartificer.townstead.client.animation.nativeclip.NativeClipRegistry.getBedrock(
                ResourceLocation.tryParse("townstead_performance:stool_sit")).orElse(null);
        if (clip != null) {
            return com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceSampler.sample(
                    clip, context.animationProgress() + 4F, Float.MAX_VALUE,
                    AnimationTargetMap.forMcaModel(context.model()));
        }
        return List.of(
                leg("right_leg", -1.28F, 0.10F, 0.04F),
                leg("left_leg", -1.28F, -0.10F, -0.04F));
    }

    private static boolean isAboveStool(LivingEntity entity, BlockPos vehiclePos) {
        return entity.level().getBlockState(vehiclePos).is(STOOLS)
                || entity.level().getBlockState(vehiclePos.below()).is(STOOLS)
                || entity.level().getBlockState(vehiclePos.above()).is(STOOLS);
    }

    private static AnimationTransform leg(String target, float xRot, float yRot, float zRot) {
        return new AnimationTransform(target, null, null, null, xRot, yRot, zRot,
                null, null, null, 1.48F, 0F,
                false, false, true, SET);
    }
}
