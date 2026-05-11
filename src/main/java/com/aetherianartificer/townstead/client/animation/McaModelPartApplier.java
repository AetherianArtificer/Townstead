package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
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

            // Bend the wear-layer companions (MCA's leftArmwear / rightArmwear /
            // etc.) with the same bend value, so the outer wear mesh follows
            // the inner limb's bend instead of staying straight and rendering
            // as a duplicate "extra arm".
            if (transform.applyBend()) {
                for (ModelPart companion : targetMap.bendCompanionsFor(transform.target())) {
                    invokeBend(companion, transform.bend(), transform.bendDirection());
                }
            }
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
        if (transform.applyBend()) {
            invokeBend(part, transform.bend(), transform.bendDirection());
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

    /**
     * Calls playerAnim's {@code IBendHelper.INSTANCE.bend(part, A, B)}
     * reflectively, where the API expects {@code A = bendDirection (axis)}
     * and {@code B = bend angle} — the opposite of the intuitive order.
     * Confirmed against playerAnim's {@code BodyPart.getBend} which packs the
     * Pair as {@code (bendAxis, bend)}.
     *
     * <p>This also matters because {@code BendHelper.bend} early-outs and
     * <i>clears</i> the bend if {@code |secondArg| < 1e-4}. With the args in
     * the wrong order, our actual bend angle ended up in the position the
     * clear-check reads, so every frame zeroed the mutator.</p>
     */
    private static void invokeBend(ModelPart part, Float bend, Float bendDirection) {
        if (bend == null || bendDirection == null) return;
        if (!Float.isFinite(bend) || !Float.isFinite(bendDirection)) return;
        EmoteReflection.applyBend(part, bendDirection, bend);
    }

    public record ApplyStats(int appliedParts, float largestDelta) {}
}
