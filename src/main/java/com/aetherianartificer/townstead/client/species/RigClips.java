package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.attachment.AttachmentPoses;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.root.attachment.AttachmentAnimation;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.Util;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plays a rig's keyframe clips ({@link RigDefinition.Animation}) on its baked bones. Once per tick the
 * first matching rule picks the clip; the body cross-fades from the previous clip over the rule's
 * transition. Values follow the Bedrock file's convention (rotation degrees, position pixels) and are
 * flipped into Java model space here, the same way the geometry loader flips the bones. Runs after the
 * model's rest reset, so it adds onto the baked pose each frame.
 */
public final class RigClips {

    private static final Map<Integer, State> STATES = new HashMap<>();
    private static final long EVICT_AFTER_MS = 10_000;
    private static final float[] WORK = new float[3];

    private static final class State {
        String rig = "";
        int current = -1;
        int previous = -1;
        float currentStart;
        float previousStart;
        float blend = 1f;
        int lastTick = Integer.MIN_VALUE;
        float lastAge = -1f;
        long touched;
    }

    private RigClips() {}

    public static void clear() { STATES.clear(); }

    public static void apply(LivingEntity entity, RigDefinition def, ModelPart root, float ageInTicks) {
        if (def == null || root == null) return;
        applyNeutral(root, def.poseBones("neutral"));
        if (def.animation() == null) return;
        Map<String, AttachmentAnimation.Clip> clips = RigAssets.animations(def.animation().file());
        if (clips == null) return;
        List<RigDefinition.AnimationRule> rules = def.animation().rules();

        State state = STATES.computeIfAbsent(entity.getId(), k -> new State());
        state.touched = Util.getMillis();
        if (STATES.size() > 256) evictStale();
        if (!def.id().equals(state.rig)) {
            state.rig = def.id();
            state.current = -1;
            state.previous = -1;
            state.lastTick = Integer.MIN_VALUE;
        }

        int tick = (int) ageInTicks;
        if (tick != state.lastTick) {
            state.lastTick = tick;
            int pick = match(entity, rules);
            if (pick != state.current) {
                state.previous = state.current;
                state.previousStart = state.currentStart;
                state.current = pick;
                state.currentStart = ageInTicks;
                state.blend = state.previous < 0 ? 1f : 0f;
            }
        }
        float dt = state.lastAge < 0f ? 0f : Math.max(0f, ageInTicks - state.lastAge);
        state.lastAge = ageInTicks;
        int timing = state.current >= 0 ? state.current : state.previous;
        float transition = timing >= 0 && timing < rules.size() ? rules.get(timing).transitionTicks() : 0f;
        state.blend = transition <= 0f ? 1f : Math.min(1f, state.blend + dt / transition);

        if (state.previous >= 0 && state.previous < rules.size() && state.blend < 1f) {
            play(root, clips, rules.get(state.previous), ageInTicks - state.previousStart, 1f - state.blend);
        }
        if (state.current >= 0 && state.current < rules.size()) {
            play(root, clips, rules.get(state.current), ageInTicks - state.currentStart, state.blend);
        }
    }

    /** Pack-authored rest correction, after setupAnim resets bones and before clips/grips. */
    static void applyNeutral(ModelPart root, List<RigDefinition.PoseBone> bones) {
        if (bones == null) return;
        for (var entry : bones) {
            var path = RigModels.bonePath(root, entry.bone());
            if (path == null) continue;
            var part = path[path.length - 1];
            part.x += entry.offset()[0]; part.y += entry.offset()[1]; part.z += entry.offset()[2];
            part.xRot += (float) Math.toRadians(entry.rotation()[0]);
            part.yRot += (float) Math.toRadians(entry.rotation()[1]);
            part.zRot += (float) Math.toRadians(entry.rotation()[2]);
        }
    }

    /** Geometry arms have no HumanoidModel item pose; give occupied grips a carrying pose. */
    public static void applyGrips(LivingEntity entity, RigDefinition def, ModelPart root, float partialTick) {
        if (def == null || def.modelType() != RigDefinition.ModelType.GEOMETRY || root == null) return;
        for (boolean off : new boolean[]{false, true}) {
            var grip = off ? def.hold().offhand() : def.hold().mainhand();
            var stack = off ? entity.getOffhandItem() : entity.getMainHandItem();
            if (grip == null || stack.isEmpty()) continue;
            ModelPart[] path = RigModels.bonePath(root, grip.bone());
            if (path == null) continue;
            ModelPart arm = path[path.length - 1];
            carryArm(arm, entity.isUsingItem() && entity.getUsedItemHand() ==
                    (off ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND),
                    off ? 0 : entity.getAttackAnim(partialTick));
        }
    }

    static void carryArm(ModelPart arm, boolean using, float attack) {
        arm.xRot -= (float) Math.toRadians(using ? 45 : 15);
        arm.zRot *= 0.35f;
        arm.xRot -= (float) Math.sin(attack * Math.PI) * 1.1f;
    }

    private static int match(LivingEntity entity, List<RigDefinition.AnimationRule> rules) {
        ConditionContext ctx = null;
        for (int i = 0; i < rules.size(); i++) {
            RigDefinition.AnimationRule rule = rules.get(i);
            if (!rule.state().isEmpty() && !AttachmentPoses.stateActive(entity, rule.state())) continue;
            if (!rule.whenJson().isEmpty()) {
                if (ctx == null) ctx = new ConditionContext(entity);
                if (!RigOutfitState.active(rule.whenJson(), ctx)) continue;
            }
            return i;
        }
        return -1;
    }

    private static void play(ModelPart root, Map<String, AttachmentAnimation.Clip> clips,
                             RigDefinition.AnimationRule rule, float elapsed, float weight) {
        if (weight <= 0f) return;
        String name = rule.clip();
        int separator = name.indexOf('#');
        if (separator > 0) {
            clips = RigAssets.animations(name.substring(0, separator));
            name = name.substring(separator + 1);
        }
        AttachmentAnimation.Clip clip = RigAssets.clip(clips, name);
        if (clip == null) return;
        float time = elapsed * rule.speed();
        time = clip.loop == AttachmentAnimation.Loop.LOOP ? time % clip.lengthTicks : Math.min(time, clip.lengthTicks);
        for (Map.Entry<String, AttachmentAnimation.BoneTrack> entry : clip.bones.entrySet()) {
            ModelPart[] path = RigModels.bonePath(root, entry.getKey());
            if (path == null) continue;
            ModelPart bone = path[path.length - 1];
            AttachmentAnimation.BoneTrack track = entry.getValue();
            if (track.rotation() != null) {
                float[] deg = track.rotation().sample(time, WORK);
                bone.xRot += (float) Math.toRadians(deg[0]) * weight;
                bone.yRot += (float) Math.toRadians(-deg[1]) * weight;
                bone.zRot += (float) Math.toRadians(-deg[2]) * weight;
            }
            if (track.position() != null) {
                float[] px = track.position().sample(time, WORK);
                bone.x += px[0] * weight;
                bone.y -= px[1] * weight;
                bone.z += px[2] * weight;
            }
            if (track.scale() != null) {
                float[] scale = track.scale().sample(time, WORK);
                bone.xScale *= 1f + (scale[0] - 1f) * weight;
                bone.yScale *= 1f + (scale[1] - 1f) * weight;
                bone.zScale *= 1f + (scale[2] - 1f) * weight;
            }
        }
    }

    private static void evictStale() {
        long now = Util.getMillis();
        STATES.values().removeIf(state -> now - state.touched > EVICT_AFTER_MS);
    }
}
