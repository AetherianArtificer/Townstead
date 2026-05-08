package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmoteRegistry;
import com.aetherianartificer.townstead.client.animation.emote.EmotecraftAnimationSourceAdapter;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEventBridge;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class McaAnimationBridge {
    private static final EmfAnimationSourceAdapter EMF_ADAPTER = new EmfAnimationSourceAdapter();
    private static final EmotecraftAnimationSourceAdapter EMOTE_ADAPTER = new EmotecraftAnimationSourceAdapter();
    private static final List<AnimationSourceAdapter> SOURCES = List.of(
            new DebugAnimationSourceAdapter(),
            EMF_ADAPTER,
            EMOTE_ADAPTER
    );

    private static final float BREAST_BASE_X_ROT = (float) Math.PI * 0.3f;

    private static boolean loggedNoSources;
    private static long lastDiagnosticTick = -200L;

    private McaAnimationBridge() {}

    /** Drop cached CEM programs so the next render reloads from the current pack stack. */
    public static void onResourcesReloaded() {
        EMF_ADAPTER.invalidate();
        EmoteReflection.invalidate();
        EMOTE_ADAPTER.invalidate();
        EmoteRegistry.reload();
        EmotecraftEventBridge.ensureRegistered();
    }

    public static <T extends LivingEntity> void apply(
            T entity,
            HumanoidModel<T> model,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        // Skip babies for now, since they have their own thing going on
        if (entity.isBaby()) return;

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

        // Capture breasts' rest-pose offset in body's LOCAL frame before any source mutates body.
        // MCA's applyVillagerDimensions has just placed breasts at root-space coords meant to
        // sit on the chest given body's CURRENT vanilla pose (e.g. crouching adds offsets to
        // compensate for body.xRot=0.5 + body.y=3.2). Inverse-rotating that root-space offset
        // through body's current rotation strips out the pose so the captured vector is
        // pose-independent; we then re-rotate it by body's full rotation after sources apply.
        ModelPart breasts = breastsPart(model);
        float localOffsetX = 0f;
        float localOffsetY = 0f;
        float localOffsetZ = 0f;
        if (breasts != null) {
            Vector3f offset = new Vector3f(
                    breasts.x - model.body.x,
                    breasts.y - model.body.y,
                    breasts.z - model.body.z);
            new Quaternionf()
                    .rotationZYX(model.body.zRot, model.body.yRot, model.body.xRot)
                    .invert()
                    .transform(offset);
            localOffsetX = offset.x;
            localOffsetY = offset.y;
            localOffsetZ = offset.z;
        }

        boolean anyAvailable = false;
        for (AnimationSourceAdapter source : SOURCES) {
            if (!source.isAvailable()) continue;
            anyAvailable = true;
            List<AnimationTransform> transforms = source.collectTransforms(context);
            McaModelPartApplier.ApplyStats stats = McaModelPartApplier.applyWithStats(source.id(), targets, transforms);
            logDiagnostic(entity, model, source.id(), transforms, stats);
        }

        syncMcaDependentParts(model, breasts, localOffsetX, localOffsetY, localOffsetZ);

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
        if (!"emf".equals(sourceId)) return;
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

    private static ModelPart breastsPart(HumanoidModel<?> model) {
        if (model instanceof VillagerEntityModelMCA<?> villagerModel) return villagerModel.breasts;
        if (model instanceof PlayerEntityExtendedModel<?> playerModel) return playerModel.breasts;
        return null;
    }

    private static void syncMcaDependentParts(
            HumanoidModel<?> model,
            ModelPart breasts,
            float localOffsetX,
            float localOffsetY,
            float localOffsetZ
    ) {
        model.hat.copyFrom(model.head);
        if (model instanceof VillagerEntityModelMCA<?> villagerModel) {
            villagerModel.leftLegwear.copyFrom(villagerModel.leftLeg);
            villagerModel.rightLegwear.copyFrom(villagerModel.rightLeg);
            villagerModel.leftArmwear.copyFrom(villagerModel.leftArm);
            villagerModel.rightArmwear.copyFrom(villagerModel.rightArm);
            villagerModel.bodyWear.copyFrom(villagerModel.body);
            applyRigidBreastsAttachment(breasts, model.body, localOffsetX, localOffsetY, localOffsetZ);
            villagerModel.breastsWear.copyFrom(villagerModel.breasts);
        } else if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.leftPants.copyFrom(playerModel.leftLeg);
            playerModel.rightPants.copyFrom(playerModel.rightLeg);
            playerModel.leftSleeve.copyFrom(playerModel.leftArm);
            playerModel.rightSleeve.copyFrom(playerModel.rightArm);
            playerModel.jacket.copyFrom(playerModel.body);
            applyRigidBreastsAttachment(breasts, model.body, localOffsetX, localOffsetY, localOffsetZ);
            playerModel.breastsWear.copyFrom(playerModel.breasts);
        }
    }

    // Treats breasts as a virtual child of body. Body's rotation rotates the captured rest-pose
    // local offset around body's pivot, and body's rotation composes with the chest's local
    // forward tilt so the chest band stays glued to the rotated/twisted torso instead of
    // floating in front of it.
    private static void applyRigidBreastsAttachment(
            ModelPart breasts,
            ModelPart body,
            float localOffsetX,
            float localOffsetY,
            float localOffsetZ
    ) {
        if (breasts == null) return;
        Quaternionf bodyRot = new Quaternionf().rotationZYX(body.zRot, body.yRot, body.xRot);
        Vector3f offset = bodyRot.transform(new Vector3f(localOffsetX, localOffsetY, localOffsetZ));
        breasts.x = body.x + offset.x;
        breasts.y = body.y + offset.y;
        breasts.z = body.z + offset.z;
        bodyRot.mul(new Quaternionf().rotationX(BREAST_BASE_X_ROT));
        Vector3f euler = bodyRot.getEulerAnglesZYX(new Vector3f());
        breasts.xRot = euler.x;
        breasts.yRot = euler.y;
        breasts.zRot = euler.z;
    }
}
