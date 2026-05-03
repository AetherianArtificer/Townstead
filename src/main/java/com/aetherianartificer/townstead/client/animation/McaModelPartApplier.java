package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class McaModelPartApplier {
    private McaModelPartApplier() {}

    public static <T extends LivingEntity> void apply(
            String sourceId,
            AnimationTargetMap<T> targetMap,
            List<AnimationTransform> transforms
    ) {
        applyWithStats(sourceId, targetMap, transforms);
    }

    public static <T extends LivingEntity> ApplyStats applyWithStats(
            String sourceId,
            AnimationTargetMap<T> targetMap,
            List<AnimationTransform> transforms
    ) {
        int applied = 0;
        float largest = 0.0F;
        for (AnimationTransform transform : transforms) {
            ModelPart part = targetMap.resolve(transform.target()).orElse(null);
            if (part == null) {
                Townstead.LOGGER.debug(
                        "[AnimationBridge] source={} unknown target={}; transform skipped",
                        sourceId,
                        transform.target());
                continue;
            }

            ApplyStats stats = apply(part, transform);
            if (stats.appliedParts() > 0) applied++;
            largest = Math.max(largest, stats.largestDelta());
        }
        return new ApplyStats(applied, largest);
    }

    private static ApplyStats apply(ModelPart part, AnimationTransform transform) {
        float beforeX = part.xRot;
        float beforeY = part.yRot;
        float beforeZ = part.zRot;
        float beforeTx = part.x;
        float beforeTy = part.y;
        float beforeTz = part.z;

        AnimationTransform.Operation operation = transform.operation();
        if (transform.applyTranslation()) {
            part.x = apply(part.x, transform.x(), operation);
            part.y = apply(part.y, transform.y(), operation);
            part.z = apply(part.z, transform.z(), operation);
        }
        part.xRot = apply(part.xRot, transform.xRot(), operation);
        part.yRot = apply(part.yRot, transform.yRot(), operation);
        part.zRot = apply(part.zRot, transform.zRot(), operation);
        if (transform.applyScale()) {
            part.xScale = apply(part.xScale, transform.xScale(), operation);
            part.yScale = apply(part.yScale, transform.yScale(), operation);
            part.zScale = apply(part.zScale, transform.zScale(), operation);
        }

        float largest = Math.max(
                Math.max(Math.abs(part.xRot - beforeX), Math.abs(part.yRot - beforeY)),
                Math.abs(part.zRot - beforeZ));
        largest = Math.max(largest, Math.max(
                Math.max(Math.abs(part.x - beforeTx), Math.abs(part.y - beforeTy)),
                Math.abs(part.z - beforeTz)));
        return new ApplyStats(largest > 0.00001F ? 1 : 0, largest);
    }

    private static float apply(float current, Float value, AnimationTransform.Operation operation) {
        if (value == null) return current;
        if (!Float.isFinite(value)) return current;
        if (Math.abs(value) > 100.0F) return current;
        return operation == AnimationTransform.Operation.ADD ? current + value : value;
    }

    public record ApplyStats(int appliedParts, float largestDelta) {}
}
