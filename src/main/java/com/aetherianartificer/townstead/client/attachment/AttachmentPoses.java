package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side state-pose evaluator for attachments. Each frame an attachment's pose
 * entries are checked against the entity — named states resolve from client-visible
 * flags, condition entries through the pheno condition registry (need conditions read
 * the synced client stores) — and the rotations ease toward the active targets over
 * each entry's transition ticks, so ears fold when sleep starts rather than snap.
 *
 * <p>Targets re-evaluate once per entity tick; easing runs per frame off the entity's
 * age clock. Active entries apply in authored order, later entries winning per bone.
 * Smoothing state is per entity+definition and evicted when stale, since entities
 * despawn without notice.</p>
 */
public final class AttachmentPoses {

    // The canonical state list lives in AttachmentDef.POSE_STATES; stateActive() below
    // is its client resolution and must cover exactly those keys.

    // Parsed conditions per def id, index-aligned with def.poses() (null = named-state
    // entry or unparseable condition). Cleared whenever a manifest arrives, so live
    // `adjust` re-broadcasts and reloads re-parse.
    private static final Map<String, List<Condition>> CONDITIONS = new ConcurrentHashMap<>();

    // Parsed whole-attachment gates per def id (Optional.empty = no gate or unparseable
    // = always render, mirroring the server-side condition fallbacks).
    private static final Map<String, java.util.Optional<Condition>> GATES = new ConcurrentHashMap<>();

    // Smoothing state per entity id; render-thread only.
    private static final Map<Integer, EntityState> STATES = new HashMap<>();
    private static final long EVICT_AFTER_MS = 10_000;
    private static final float SNAP = 0.01f;

    private AttachmentPoses() {}

    public static void onManifest() {
        CONDITIONS.clear();
        GATES.clear();
        STATES.clear();
    }

    /**
     * Whether the definition's whole-attachment {@code when} gate holds for this bearer
     * (a beard only masculine villagers wear). No gate or an unparseable one renders.
     */
    public static boolean defActive(LivingEntity entity, AttachmentDef def) {
        if (def.whenJson().isEmpty()) return true;
        var gate = GATES.computeIfAbsent(def.id(), k -> {
            try {
                return java.util.Optional.ofNullable(
                        Conditions.parse(JsonParser.parseString(def.whenJson())));
            } catch (Exception e) {
                return java.util.Optional.empty();
            }
        });
        return gate.map(condition -> condition.test(new ConditionContext(entity))).orElse(true);
    }

    /** The eased pose for this entity+attachment right now, or null when at rest. */
    @Nullable
    public static Sample sample(LivingEntity entity, AttachmentDef def, float ageInTicks) {
        if (def.poses().isEmpty()) return null;
        EntityState entityState = STATES.computeIfAbsent(entity.getId(), k -> new EntityState());
        entityState.lastTouchMs = Util.getMillis();
        if (STATES.size() > 256) evictStale();
        DefPoseState state = entityState.defs.computeIfAbsent(def.id(), k -> new DefPoseState());

        int tick = (int) ageInTicks;
        if (state.lastEvalTick != tick) {
            state.lastEvalTick = tick;
            retarget(entity, def, state);
        }
        float dt = state.lastAge < 0 ? Float.MAX_VALUE : Math.max(0f, ageInTicks - state.lastAge);
        state.lastAge = ageInTicks;
        boolean moving = ease(state.whole, dt) | easeBones(state.bones, dt);

        if (!moving && atRest(state)) return null;
        Map<String, float[]> bones = new LinkedHashMap<>();
        state.bones.forEach((name, channel) -> bones.put(name, channel.current));
        return new Sample(state.whole.current, bones);
    }

    /** Current eased rotations: whole-attachment (def convention, ZYX degrees) + per-bone (geo convention, degrees). */
    public record Sample(float[] rotation, Map<String, float[]> boneRotations) {}

    private static void retarget(LivingEntity entity, AttachmentDef def, DefPoseState state) {
        zero(state.whole.target);
        for (Channel channel : state.bones.values()) zero(channel.target);

        List<Condition> conditions = CONDITIONS.computeIfAbsent(def.id(), k -> parseConditions(def));
        ConditionContext ctx = new ConditionContext(entity);
        List<AttachmentDef.PoseEntry> poses = def.poses();
        for (int i = 0; i < poses.size(); i++) {
            AttachmentDef.PoseEntry pose = poses.get(i);
            boolean active = pose.state().isEmpty()
                    ? conditions.get(i) != null && conditions.get(i).test(ctx)
                    : stateActive(entity, pose.state());
            if (!active) continue;
            if (pose.rotation() != null) {
                set(state.whole.target, pose.rotation());
                state.whole.transition = pose.transitionTicks();
            }
            pose.boneRotations().forEach((name, rotation) -> {
                Channel channel = state.bones.computeIfAbsent(name, k -> new Channel());
                set(channel.target, rotation);
                channel.transition = pose.transitionTicks();
            });
        }
    }

    private static List<Condition> parseConditions(AttachmentDef def) {
        List<Condition> out = new ArrayList<>(def.poses().size());
        for (AttachmentDef.PoseEntry pose : def.poses()) {
            Condition condition = null;
            if (!pose.conditionJson().isEmpty()) {
                try {
                    condition = Conditions.parse(JsonParser.parseString(pose.conditionJson()));
                } catch (Exception ignored) {
                }
            }
            out.add(condition);
        }
        return out;
    }

    /** Client resolution of a canonical pose-state name (shared with keyframe animation triggers). */
    public static boolean stateActive(LivingEntity entity, String state) {
        return switch (state) {
            case "sleeping" -> entity.isSleeping();
            case "sitting" -> entity.isPassenger();
            case "sprinting" -> entity.isSprinting();
            case "sneaking" -> entity.isCrouching();
            case "swimming" -> entity.isSwimming();
            case "gliding" -> entity.isFallFlying();
            case "flying" -> entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().flying;
            case "moving" -> entity.walkAnimation.speed() > 0.1f;
            case "hurt" -> entity.hurtTime > 0;
            case "attacking" -> entity.swinging || (entity instanceof Mob mob && mob.isAggressive());
            case "panicking" -> entity instanceof VillagerEntityMCA villager
                    && villager.getVillagerBrain().isPanicking();
            case "working" -> entity instanceof VillagerEntityMCA villager
                    && villager.getVillagerBrain().getCurrentJob() != Chore.NONE;
            case "wearing_helmet", "wearing_chestplate", "wearing_leggings", "wearing_boots" -> {
                var slot = AttachmentDef.equipmentSlot(state.substring("wearing_".length()));
                yield slot != null && !entity.getItemBySlot(slot).isEmpty();
            }
            default -> false;
        };
    }

    // --- easing ---

    private static boolean easeBones(Map<String, Channel> bones, float dt) {
        boolean moving = false;
        for (Channel channel : bones.values()) moving |= ease(channel, dt);
        return moving;
    }

    /** Exponential ease of current toward target (~95% covered after `transition` ticks). */
    private static boolean ease(Channel channel, float dt) {
        float alpha = channel.transition <= 0f ? 1f
                : (float) (1.0 - Math.exp(-3.0 * dt / channel.transition));
        boolean moving = false;
        for (int i = 0; i < 3; i++) {
            float delta = channel.target[i] - channel.current[i];
            if (Math.abs(delta) < SNAP) {
                channel.current[i] = channel.target[i];
                continue;
            }
            channel.current[i] += delta * Math.min(1f, alpha);
            moving = true;
        }
        return moving;
    }

    private static boolean atRest(DefPoseState state) {
        if (!isZero(state.whole.current)) return false;
        for (Channel channel : state.bones.values()) {
            if (!isZero(channel.current)) return false;
        }
        return true;
    }

    private static boolean isZero(float[] v) {
        return Math.abs(v[0]) < SNAP && Math.abs(v[1]) < SNAP && Math.abs(v[2]) < SNAP;
    }

    private static void zero(float[] v) {
        v[0] = 0f;
        v[1] = 0f;
        v[2] = 0f;
    }

    private static void set(float[] v, float[] from) {
        v[0] = from[0];
        v[1] = from[1];
        v[2] = from[2];
    }

    private static void evictStale() {
        long now = Util.getMillis();
        STATES.values().removeIf(state -> now - state.lastTouchMs > EVICT_AFTER_MS);
    }

    private static final class EntityState {
        long lastTouchMs;
        final Map<String, DefPoseState> defs = new HashMap<>();
    }

    private static final class DefPoseState {
        int lastEvalTick = Integer.MIN_VALUE;
        float lastAge = -1f;
        final Channel whole = new Channel();
        final Map<String, Channel> bones = new HashMap<>();
    }

    private static final class Channel {
        final float[] current = new float[3];
        final float[] target = new float[3];
        float transition = 4f;
    }
}
