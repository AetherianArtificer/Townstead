package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class McaAnimationBridge {
    private static final List<AnimationSourceAdapter> SOURCES = List.of(
            new DebugAnimationSourceAdapter(),
            new FreshEmfAnimationSourceAdapter()
    );

    private static boolean loggedNoSources;
    private static long lastDiagnosticTick = -200L;

    private McaAnimationBridge() {}

    public static <T extends LivingEntity> void apply(
            T entity,
            HumanoidModel<T> model,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        McaAnimationParameters parameters = McaAnimationParameters.from(
                entity,
                model,
                limbAngle,
                limbDistance,
                animationProgress,
                headYaw);
        McaRigScale rigScale = McaRigScale.from(entity, model);
        AnimationSourceContext<T> context = new AnimationSourceContext<>(
                entity, model, parameters, rigScale, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        AnimationTargetMap<T> targets = AnimationTargetMap.forMcaModel(model);
        boolean anyAvailable = false;

        for (AnimationSourceAdapter source : SOURCES) {
            if (!source.isAvailable()) continue;
            anyAvailable = true;
            List<AnimationTransform> transforms = source.collectTransforms(context);
            McaModelPartApplier.ApplyStats stats = McaModelPartApplier.applyWithStats(source.id(), targets, transforms);
            logDiagnostic(entity, model, source.id(), transforms, stats);
            syncMcaDependentParts(model);
        }

        if (!anyAvailable && !loggedNoSources) {
            loggedNoSources = true;
            Townstead.LOGGER.info("[AnimationBridge] no animation source adapters are currently available; bridge skipped");
        }
    }

    private static <T extends LivingEntity> void logDiagnostic(
            T entity,
            HumanoidModel<T> model,
            String sourceId,
            List<AnimationTransform> transforms,
            McaModelPartApplier.ApplyStats stats
    ) {
        if (!"fresh_emf".equals(sourceId)) return;
        long tick = entity.level().getGameTime();
        if (tick - lastDiagnosticTick < 120L) return;
        lastDiagnosticTick = tick;
        Townstead.LOGGER.info(
                "[AnimationBridge] diagnostic source={} entity={} model={} transforms={} appliedParts={} largestDelta={} sample={}",
                sourceId,
                entity.getType().builtInRegistryHolder().key().location(),
                model.getClass().getName(),
                transforms.size(),
                stats.appliedParts(),
                stats.largestDelta(),
                summarizeTransforms(transforms));
    }

    private static String summarizeTransforms(List<AnimationTransform> transforms) {
        if (transforms.isEmpty()) return "[]";
        StringBuilder builder = new StringBuilder("[");
        int limit = Math.min(4, transforms.size());
        for (int i = 0; i < limit; i++) {
            AnimationTransform transform = transforms.get(i);
            if (i > 0) builder.append(", ");
            builder.append(transform.target())
                    .append(":rx=").append(transform.xRot())
                    .append(",ry=").append(transform.yRot())
                    .append(",rz=").append(transform.zRot())
                    .append(",tx=").append(transform.x())
                    .append(",ty=").append(transform.y())
                    .append(",tz=").append(transform.z());
        }
        if (transforms.size() > limit) builder.append(", ...");
        return builder.append(']').toString();
    }

    private static void syncMcaDependentParts(HumanoidModel<?> model) {
        if (model instanceof VillagerEntityModelMCA<?> villagerModel) {
            villagerModel.leftLegwear.copyFrom(villagerModel.leftLeg);
            villagerModel.rightLegwear.copyFrom(villagerModel.rightLeg);
            villagerModel.leftArmwear.copyFrom(villagerModel.leftArm);
            villagerModel.rightArmwear.copyFrom(villagerModel.rightArm);
            villagerModel.bodyWear.copyFrom(villagerModel.body);
            villagerModel.breastsWear.copyFrom(villagerModel.breasts);
        } else if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.leftPants.copyFrom(playerModel.leftLeg);
            playerModel.rightPants.copyFrom(playerModel.rightLeg);
            playerModel.leftSleeve.copyFrom(playerModel.leftArm);
            playerModel.rightSleeve.copyFrom(playerModel.rightArm);
            playerModel.jacket.copyFrom(playerModel.body);
            playerModel.breastsWear.copyFrom(playerModel.breasts);
        }
    }
}
