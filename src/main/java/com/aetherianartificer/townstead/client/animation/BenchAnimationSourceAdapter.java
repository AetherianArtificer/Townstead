package com.aetherianartificer.townstead.client.animation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;

import java.util.List;

/** A relaxed, bent-knee passenger silhouette for blocks tagged as benches. */
public final class BenchAnimationSourceAdapter implements AnimationSourceAdapter {
    private static final TagKey<Block> BENCHES = TagKey.create(Registries.BLOCK,
            ResourceLocation.tryParse("townstead_hangouts:benches"));
    private static final AnimationTransform.Operation SET = AnimationTransform.Operation.SET;

    @Override
    public String id() {
        return "bench";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public List<AnimationTransform> collectTransforms(AnimationSourceContext context) {
        LivingEntity entity = context.entity();
        Entity vehicle = entity.getVehicle();
        if (vehicle == null || !isAboveBench(entity, vehicle.blockPosition())) return List.of();
        return List.of(
                leg("right_leg", -1.34F, 0.08F, 0.035F),
                leg("left_leg", -1.34F, -0.08F, -0.035F));
    }

    private static boolean isAboveBench(LivingEntity entity, BlockPos vehiclePos) {
        return entity.level().getBlockState(vehiclePos).is(BENCHES)
                || entity.level().getBlockState(vehiclePos.below()).is(BENCHES)
                || entity.level().getBlockState(vehiclePos.above()).is(BENCHES);
    }

    private static AnimationTransform leg(String target, float xRot, float yRot, float zRot) {
        return new AnimationTransform(target, null, null, null, xRot, yRot, zRot,
                null, null, null, 1.22F, 0F,
                false, false, true, SET);
    }
}
