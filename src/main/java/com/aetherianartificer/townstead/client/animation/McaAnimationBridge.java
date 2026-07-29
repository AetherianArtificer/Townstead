package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmoteRegistry;
import com.aetherianartificer.townstead.client.animation.emote.EmotecraftAnimationSourceAdapter;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEventBridge;
import com.aetherianartificer.townstead.mixin.accessor.VillagerEditorScreenAccessor;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.List;

public final class McaAnimationBridge {
    private static final EmfAnimationSourceAdapter EMF_ADAPTER = new EmfAnimationSourceAdapter();
    private static final RootAnimationSourceAdapter ROOT_ADAPTER = new RootAnimationSourceAdapter();
    private static final EmotecraftAnimationSourceAdapter EMOTE_ADAPTER = new EmotecraftAnimationSourceAdapter();
    // NOTE: DebugAnimationSourceAdapter is intentionally NOT registered here. It waves every
    // villager's right arm and keys off DEBUG_VILLAGER_AI, which is the general AI-logging flag —
    // leaving it wired in meant turning on AI debug logging visibly animated all villagers. Re-add
    // it locally only when specifically testing the animation hook.
    // Order is layered, last writer wins: EMF/Fresh-Animations is the base, the origin pose layer
    // (crouch/...) overrides it, and an Emotecraft emote overrides that on the bones it animates.
    private static final List<AnimationSourceAdapter> SOURCES = List.of(
            EMF_ADAPTER,
            ROOT_ADAPTER,
            EMOTE_ADAPTER
    );

    // MCA 7.7+ bakes its villager/player models' animation parent from the vanilla PLAYER
    // layer, so an installed EMF animates MCA models natively at render time. Running our
    // CEM evaluator on the same bones double-drives them with a different clock (and EMF
    // re-poses at render anyway, stomping what we write). Custom rigs stay ours: they are
    // baked EMF-free (RigModels.bakeLayer) so only our evaluator can animate them.
    //? if neoforge {
    private static final boolean MCA_NATIVE_EMF = true;
    //?} else {
    /*private static final boolean MCA_NATIVE_EMF = false;
    *///?}

    private static final float BREAST_BASE_X_ROT = (float) Math.PI * 0.3f;
    private static final float BREAST_TILT_DAMPING = 0.5f;

    private static boolean loggedNoSources;
    private static long lastDiagnosticTick = -200L;

    private McaAnimationBridge() {}

    /** Drop cached CEM programs so the next render reloads from the current pack stack. */
    public static void onResourcesReloaded() {
        EmfCompat.register();
        EMF_ADAPTER.invalidate();
        EmoteReflection.invalidate();
        EMOTE_ADAPTER.invalidate();
        EmoteRegistry.reload();
        com.aetherianartificer.townstead.client.animation.emote.EmoteCoverage.invalidate();
        EmotecraftEventBridge.ensureRegistered();
    }

    /** Drive an MCA host/player model: targets resolve from the model's standard humanoid parts. */
    public static <T extends LivingEntity> void apply(
            T entity,
            HumanoidModel<T> model,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        apply(entity, model, null, null, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
    }

    /**
     * Drive an alternate species rig: targets resolve through the rig definition's bone map, so a
     * custom rig with arbitrary bone names is animated by the same sources. {@code rigRoot}/{@code
     * rigDef} non-null select the rig target map; both null falls back to the MCA humanoid map.
     */
    public static <T extends LivingEntity> void applyRig(
            T entity,
            HumanoidModel<T> model,
            ModelPart rigRoot,
            com.aetherianartificer.townstead.root.rig.RigDefinition rigDef,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        apply(entity, model, rigRoot, rigDef, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
    }

    private static <T extends LivingEntity> void apply(
            T entity,
            HumanoidModel<T> model,
            ModelPart rigRoot,
            com.aetherianartificer.townstead.root.rig.RigDefinition rigDef,
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
            // A non-humanoid rig anchors the host head bone onto its own head via
            // RigWearables.poseAt, which leaves head.xyz offset on the shared model.
            // Clear it too, or switching back to a humanoid root renders a detached head.
            model.head.x = 0f;
            model.head.y = 0f;
            model.head.z = 0f;
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

        // Skip gliders: fall-flight poses the whole body horizontal in setupRotations,
        // and walk/idle transforms layered on top bend the model in the wrong space.
        if (entity.isFallFlying()) return;

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
        boolean rigTargets = rigRoot != null && rigDef != null;
        AnimationTargetMap<T> targets = rigTargets
                ? AnimationTargetMap.forRig(rigRoot, rigDef)
                : AnimationTargetMap.forMcaModel(model);

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
        clearStaleResidue(targets);
        if (!rigTargets) restoreHostCrouch(model);
        BendStateRegistry.clearEntity(entity.getUUID());

        boolean anyAvailable = false;
        for (AnimationSourceAdapter source : SOURCES) {
            if (source == EMF_ADAPTER && MCA_NATIVE_EMF && !rigTargets) continue;
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
        if (!com.aetherianartificer.townstead.TownsteadConfig.DEBUG_LOGGING.get()) return;
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

    // The parts an emote can dislocate. setupAnim does not refresh every channel each frame, so an
    // emote (especially a Bedrock/geckolib one with position keyframes) leaves residue after it ends.
    // Reset the stale channels to the model default each frame; an active gait/pose/emote source
    // re-applies below. Scale is always kept (the proportions gene owns it).
    //
    // - TRANSLATION leaks on every part (setupAnim never resets translation), so reset it everywhere.
    // - ROTATION: setupAnim reassigns head xRot/yRot (look) and arm/leg rotation (gait) every frame, so
    //   those are never stale and are kept. But it does NOT reset body xRot/zRot (only body.yRot=0) or
    //   head zRot, so a Bedrock emote that pitches/rolls the torso or tilts the head leaves them stuck
    //   ("dislocated" skeletownie). Let those reset to default here too.
    private static final String[] TRANSLATABLE_TARGETS =
            {"head", "body", "left_arm", "right_arm", "left_leg", "right_leg"};

    private static <T extends LivingEntity> void clearStaleResidue(AnimationTargetMap<T> targets) {
        for (String target : TRANSLATABLE_TARGETS) {
            ModelPart part = targets.resolve(target).orElse(null);
            if (part == null) continue;
            float xr = part.xRot, yr = part.yRot, zr = part.zRot;
            float xs = part.xScale, ys = part.yScale, zs = part.zScale;
            part.resetPose();
            if ("body".equals(target)) {
                // Drop all body rotation: setupAnim only re-zeroes yRot, so xRot/zRot residue persists.
                // The pose/EMF/emote sources re-apply body rotation below if any is active.
            } else if ("head".equals(target)) {
                part.xRot = xr;
                part.yRot = yr;
                // drop head zRot (stale emote head-tilt; setupAnim never resets it)
            } else {
                part.xRot = xr;
                part.yRot = yr;
                part.zRot = zr;
            }
            part.xScale = xs;
            part.yScale = ys;
            part.zScale = zs;
        }
    }

    /**
     * Put back the crouch that {@code HumanoidModel.setupAnim} applied and {@link #clearStaleResidue}
     * just wiped. Vanilla's crouch lives almost entirely in part TRANSLATIONS (plus body xRot), which
     * are exactly the channels the residue clear resets to the model default — so without this the
     * model stands upright while the renderer still drops a crouching entity by 0.125, which reads as
     * sinking into the floor. An installed EMF/Fresh-Animations pack hid this: its sneak animation
     * re-posed the model afterwards. Values mirror vanilla; the arm xRot tilt is NOT re-applied
     * because the residue clear keeps limb rotations, so adding it again would double the bend.
     *
     * <p>Runs before the sources, so a pack's own sneak animation still overrides it. Alternate rigs
     * are excluded: {@link RootAnimationSourceAdapter} re-asserts their crouch through the rig bone
     * map, deliberately layered on top of the pack pose.</p>
     */
    private static void restoreHostCrouch(HumanoidModel<?> model) {
        if (!model.crouching) return;
        model.body.xRot = 0.5f;
        model.body.y = 3.2f;
        model.head.y = 4.2f;
        model.leftArm.y = 5.2f;
        model.rightArm.y = 5.2f;
        model.leftLeg.y = 12.2f;
        model.rightLeg.y = 12.2f;
        model.leftLeg.z = 4.0f;
        model.rightLeg.z = 4.0f;
    }

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
        // New MCA wraps GUI preview renders (editor, skin library) in PreviewEntityAnimation,
        // swapping the entity's tickCount to wall-clock so EMF/CEM animates while paused. Any
        // render inside that window has a corrupted CEM clock, whichever preview entity is drawn.
        if (PreviewClock.isActive()) return true;
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        Screen screen = client.screen;
        if (!(screen instanceof VillagerEditorScreen editor)) return false;
        // Identity fallback for MCA builds without PreviewEntityAnimation. The editor renders two
        // entities: the main preview and villagerVisualization (preset compare + selection grids).
        return editor.getVillager() == entity
                || ((VillagerEditorScreenAccessor) editor).townstead$getVillagerVisualization() == entity;
    }

    // Reflective: the compile jar may lag behind the runtime MCA (and the 1.20.1 build's package
    // remap rewrites the literal to a class old MCA doesn't have). Absent => identity check only.
    private static final class PreviewClock {
        private static final MethodHandle GET_ACTIVE_PARTIAL_TICK = resolve();

        private static MethodHandle resolve() {
            try {
                Class<?> clazz = Class.forName("net.conczin.mca.client.gui.PreviewEntityAnimation");
                return MethodHandles.publicLookup().unreflect(clazz.getMethod("getActivePartialTick"));
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }

        static boolean isActive() {
            if (GET_ACTIVE_PARTIAL_TICK == null) return false;
            try {
                return GET_ACTIVE_PARTIAL_TICK.invoke() != null;
            } catch (Throwable ignored) {
                return false;
            }
        }
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
            syncBreasts(breasts, model.body, localOffsetX, localOffsetY, localOffsetZ);
            villagerModel.breastsWear.copyFrom(villagerModel.breasts);
        } else if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.leftPants.copyFrom(playerModel.leftLeg);
            playerModel.rightPants.copyFrom(playerModel.rightLeg);
            playerModel.leftSleeve.copyFrom(playerModel.leftArm);
            playerModel.rightSleeve.copyFrom(playerModel.rightArm);
            playerModel.jacket.copyFrom(playerModel.body);
            syncBreasts(breasts, model.body, localOffsetX, localOffsetY, localOffsetZ);
            playerModel.breastsWear.copyFrom(playerModel.breasts);
        }
    }

    // New MCA (7.7.23+) keeps the breast transform body-local (applyVillagerDimensions sets it each
    // frame) and composes the torso pose at render in CommonVillagerModel.renderCommon
    // (getBodyPart().translateAndRotate before drawing the chest). Re-attaching the breasts to the
    // body here would apply the torso transform a SECOND time — visible as the chest sliding off the
    // body during emotes that move the torso a lot. So on new MCA we leave the breasts at the
    // body-local rest pose MCA already set. Old MCA (1.20.1) renders the chest at the model root
    // without composing the torso, so it still needs the manual rigid attachment.
    @SuppressWarnings("unused")
    private static void syncBreasts(ModelPart breasts, ModelPart body, float lox, float loy, float loz) {
        //? if forge {
        /*applyRigidBreastsAttachment(breasts, body, lox, loy, loz);
        *///?}
    }

    // Treats breasts as a virtual child of body. Body's rotation rotates the captured rest-pose
    // local offset around body's pivot, and body's rotation composes with the chest's local
    // forward tilt so the chest band stays glued to the rotated/twisted torso instead of
    // floating in front of it.
    @SuppressWarnings("unused") // called only on the forge (old-MCA) branch of syncBreasts
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
