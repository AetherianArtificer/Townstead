package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmoteRegistry;
import com.aetherianartificer.townstead.client.animation.emote.EmotecraftAnimationSourceAdapter;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEventBridge;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class McaAnimationBridge {
    private static final EmfAnimationSourceAdapter EMF_ADAPTER = new EmfAnimationSourceAdapter();
    private static final EmotecraftAnimationSourceAdapter EMOTE_ADAPTER = new EmotecraftAnimationSourceAdapter();
    // NOTE: DebugAnimationSourceAdapter is intentionally NOT registered here. It waves every
    // villager's right arm and keys off DEBUG_VILLAGER_AI, which is the general AI-logging flag —
    // leaving it wired in meant turning on AI debug logging visibly animated all villagers. Re-add
    // it locally only when specifically testing the animation hook.
    private static final List<AnimationSourceAdapter> SOURCES = List.of(
            EMF_ADAPTER,
            EMOTE_ADAPTER
    );

    private static final float BREAST_BASE_X_ROT = (float) Math.PI * 0.3f;
    private static final float BREAST_TILT_DAMPING = 0.5f;

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
        // MCA's editor stomps tickCount to wall-clock, breaking CEM time.
        // Skip sources, reset body translation, re-attach wear layers.
        if (isEditorPreview(entity)) {
            model.body.x = 0f;
            model.body.y = 0f;
            model.body.z = 0f;
            ModelPart editorBreasts = breastsPart(model);
            float ox = 0f, oy = 0f, oz = 0f;
            if (editorBreasts != null) {
                ox = editorBreasts.x;
                oy = editorBreasts.y;
                oz = editorBreasts.z;
            }
            syncMcaDependentParts(model, editorBreasts, ox, oy, oz);
            return;
        }

        // Skip MCA babies (their own swaying animation runs on a different code path).
        // Vanilla isBaby() returns true for any MCA young (TODDLER/CHILD/TEEN) because
        // they all have negative age, so check the MCA AgeState directly to keep
        // emotes / CEM animations on teens and children.
        if (entity instanceof VillagerLike<?> villagerLike) {
            if (villagerLike.getAgeState() == AgeState.BABY) return;
        } else if (entity.isBaby()) {
            return;
        }

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

        // Capture breasts' rest-pose offset relative to body's rest pose. MCA's
        // applyVillagerDimensions has just written breasts.xyz in absolute model
        // coordinates assuming body is at rest (translation 0,0,0; rotation 0,0,0).
        // We do NOT subtract model.body.xyz here: vanilla HumanoidModel.setupAnim
        // does not reset body.x or body.z between frames (it only touches body.y
        // for crouching and body rotations for active poses), so model.body.xyz
        // retains stale translation from the previous frame's source (CEM keyframes
        // body.x via translation channels). Subtracting that stale value gave a
        // localOffset that drifts frame to frame, which the user observed as
        // breasts floating left and right of the torso during walks. body's rest
        // pose is the right reference frame here.
        ModelPart breasts = breastsPart(model);
        float localOffsetX = 0f;
        float localOffsetY = 0f;
        float localOffsetZ = 0f;
        if (breasts != null) {
            localOffsetX = breasts.x;
            localOffsetY = breasts.y;
            localOffsetZ = breasts.z;
        }

        // Clear last frame's bend on every bendable part before sources write
        // this frame's bend. bendylib's bend mutator persists on the ModelPart
        // until explicitly cleared, so without this the limb stays bent forever
        // after an emote ends (and frame-to-frame within an emote, if a part
        // toggles between bent and unbent poses). Clearing first matches
        // setupAnim's "every frame is fresh" semantic for rotations.
        clearStaleBend(targets);
        BendStateRegistry.clearEntity(entity.getUUID());

        boolean anyAvailable = false;
        for (AnimationSourceAdapter source : SOURCES) {
            if (!source.isAvailable()) continue;
            anyAvailable = true;
            List<AnimationTransform> transforms = source.collectTransforms(context);
            McaModelPartApplier.ApplyStats stats = McaModelPartApplier.applyWithStats(source.id(), targets, transforms);
            for (AnimationTransform t : transforms) {
                if (t.applyBend() && t.bend() != null && t.bendDirection() != null) {
                    BendStateRegistry.put(entity.getUUID(), t.target(),
                            t.bendDirection(), t.bend());
                }
            }
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
        if (!"emf".equals(sourceId) && !("emotes".equals(sourceId) && !transforms.isEmpty())) return;
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

    private static final String[] BENDABLE_TARGETS = {"left_arm", "right_arm", "left_leg", "right_leg"};

    private static <T extends LivingEntity> void clearStaleBend(AnimationTargetMap<T> targets) {
        if (!EmoteReflection.isBendylibAvailable()) return;
        for (String target : BENDABLE_TARGETS) {
            ModelPart part = targets.resolve(target).orElse(null);
            if (part != null) EmoteReflection.applyBend(part, 0f, 0f);
            for (ModelPart companion : targets.bendCompanionsFor(target)) {
                EmoteReflection.applyBend(companion, 0f, 0f);
            }
        }
    }

    private static boolean isEditorPreview(LivingEntity entity) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        Screen screen = client.screen;
        return screen instanceof VillagerEditorScreen editor && editor.getVillager() == entity;
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
        // Pure rigid attachment composes body.xRot with the local 0.3π forward tilt, but the
        // chest cube extends further below its pivot than above, so heavy forward body lean
        // reads as over-tilt. Damp the local tilt linearly with forward body lean — idle/walk
        // (xRot ≈ 0) keep the full tilt, crouching/leaning ease it off.
        float dampedTilt = Math.max(0f, BREAST_BASE_X_ROT - Math.max(0f, body.xRot) * BREAST_TILT_DAMPING);
        bodyRot.mul(new Quaternionf().rotationX(dampedTilt));
        Vector3f euler = bodyRot.getEulerAnglesZYX(new Vector3f());
        breasts.xRot = euler.x;
        breasts.yRot = euler.y;
        breasts.zRot = euler.z;
    }
}
