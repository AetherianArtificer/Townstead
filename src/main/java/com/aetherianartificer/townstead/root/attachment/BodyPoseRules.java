package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;
import com.aetherianartificer.townstead.root.rig.RigDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/** What state a worn attachment is in, decided the same on the client that draws it and the server that judges it. */
public final class BodyPoseRules {

    private static final Map<String, Optional<Condition>> CONDITIONS = new ConcurrentHashMap<>();
    // When a clip with a set length started, by person, attachment and animation entry. Server only.
    private static final Map<LivingEntity, Map<String, Map<Integer, Long>>> TIMED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final long IDLE_WINDOW = 80;
    private static final long IDLE_LOOKBACK = 3;
    private static final long IDLE_MAX_COOLDOWN = 16;

    private BodyPoseRules() {}

    /** The resolved morph state for one attachment on one entity. */
    public record Morphs(float @Nullable [] whole, Map<String, float[]> boneScales,
                         Map<String, float[]> rotations, List<String> hiddenBones) {
        public static final Morphs NONE = new Morphs(null, Map.of(), Map.of(), List.of());
    }

    /**
     * Evaluates the attachment's morph channels and visibility rules against the size values rolled on the
     * allele of the gene that granted it. Scale channels weight per axis ({@code 1 + (value - 1) * axis}),
     * rotation channels turn by {@code rotate × value} degrees (geo convention, merged with pose deltas), and
     * visibility rules hide bones outside their channel bounds.
     */
    public static Morphs morphs(LivingEntity entity, AttachmentDef def, AllelePayload payload) {
        if (def.morph().isEmpty() && def.visibility().isEmpty()) return Morphs.NONE;
        if (payload.channels().isEmpty() && def.body().values().isEmpty()) return Morphs.NONE;
        float[] whole = null;
        Map<String, float[]> boneScales = null;
        Map<String, float[]> rotations = null;
        for (AttachmentDef.MorphChannel channel : def.morph()) {
            Float value = channelValue(entity, def, payload, channel.channel());
            if (value == null) continue;
            if (channel.axes() != null && value != 1f) {
                float[] factors = new float[3];
                for (int i = 0; i < 3; i++) {
                    factors[i] = Math.max(0.05f, 1f + (value - 1f) * channel.axes()[i]);
                }
                if (channel.bones().isEmpty()) {
                    if (whole == null) whole = new float[]{1f, 1f, 1f};
                    for (int i = 0; i < 3; i++) whole[i] *= factors[i];
                } else {
                    if (boneScales == null) boneScales = new LinkedHashMap<>();
                    for (String morphBone : channel.bones()) {
                        float[] slot = boneScales.computeIfAbsent(morphBone, k -> new float[]{1f, 1f, 1f});
                        for (int i = 0; i < 3; i++) slot[i] *= factors[i];
                    }
                }
            }
            if (channel.rotate() != null && value != 0f) {
                if (rotations == null) rotations = new LinkedHashMap<>();
                for (String morphBone : channel.bones()) {
                    float[] slot = rotations.computeIfAbsent(morphBone, k -> new float[3]);
                    for (int i = 0; i < 3; i++) slot[i] += channel.rotate()[i] * value;
                }
            }
        }
        List<String> hiddenBones = null;
        for (AttachmentDef.VisibilityRule rule : def.visibility()) {
            Float value = channelValue(entity, def, payload, rule.channel());
            if (value == null) continue;   // no roll for the channel: stay visible
            boolean visible = (Float.isNaN(rule.below()) || value < rule.below())
                    && (Float.isNaN(rule.above()) || value > rule.above());
            if (visible) continue;
            if (hiddenBones == null) hiddenBones = new ArrayList<>();
            hiddenBones.addAll(rule.bones());
        }
        if (whole == null && boneScales == null && rotations == null && hiddenBones == null) return Morphs.NONE;
        return new Morphs(whole,
                boneScales == null ? Map.of() : boneScales,
                rotations == null ? Map.of() : rotations,
                hiddenBones == null ? List.of() : hiddenBones);
    }

    /** A channel's live value, else its rolled one; a lone anonymous legacy roll answers for any name. Null = unrolled. */
    private static Float channelValue(LivingEntity entity, AttachmentDef def, AllelePayload payload, String name) {
        Float live = BodyValues.channel(entity, def, name);
        if (live != null) return live;
        Float value = payload.channels().get(name);
        if (value == null && payload.channels().size() == 1) {
            value = payload.channels().get(AllelePayload.LEGACY_CHANNEL);
        }
        return value;
    }

    /** Resolves a canonical pose-state name (shared with keyframe animation triggers). */
    public static boolean stateActive(LivingEntity entity, String state) {
        return switch (state) {
            case "sleeping" -> entity.isSleeping();
            case "folded" -> BodyVolumes.folded(entity);
            case "sitting" -> entity.isPassenger();
            case "sprinting" -> entity.isSprinting();
            case "sneaking" -> entity.isCrouching();
            case "swimming" -> entity.isSwimming();
            case "gliding" -> entity.isFallFlying();
            case "parachuting" -> com.aetherianartificer.townstead.root.locomotion.ParachutePhysics.isOpen(entity);
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

    private static boolean conditionHolds(LivingEntity entity, String json) {
        Optional<Condition> condition = CONDITIONS.computeIfAbsent(json, key -> {
            try {
                return Optional.ofNullable(Conditions.parse(JsonParser.parseString(key)));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        return condition.isPresent() && condition.get().test(new ConditionContext(entity));
    }

    /** The whole-attachment rotation its state poses hold it at, without the ease into it. Null at rest. */
    @Nullable
    static float[] poseRotation(LivingEntity entity, AttachmentDef def) {
        float[] rotation = null;
        for (AttachmentDef.PoseEntry pose : def.poses()) {
            if (pose.rotation() != null && active(entity, pose.state(), pose.conditionJson())) rotation = pose.rotation();
        }
        return rotation;
    }

    /** The bone rotations its state poses hold it at. Later poses win per bone, as they do when drawn. */
    static Map<String, float[]> poseBones(LivingEntity entity, AttachmentDef def) {
        if (def.poses().isEmpty()) return Map.of();
        Map<String, float[]> out = new LinkedHashMap<>();
        for (AttachmentDef.PoseEntry pose : def.poses()) {
            if (active(entity, pose.state(), pose.conditionJson())) out.putAll(pose.boneRotations());
        }
        return out;
    }

    private static boolean active(LivingEntity entity, String state, String conditionJson) {
        return state.isEmpty() ? !conditionJson.isEmpty() && conditionHolds(entity, conditionJson) : stateActive(entity, state);
    }

    /** Where a clip's time is now. Looping clips run on the world's clock; the rest run from when they started. Null when over. */
    private static Float clipTime(AttachmentAnimation.Clip clip, float world, @Nullable Long started) {
        if (clip.lengthTicks <= 0f) return null;
        if (clip.loop == AttachmentAnimation.Loop.LOOP) return world % clip.lengthTicks;
        if (started == null) return clip.loop == AttachmentAnimation.Loop.HOLD ? clip.lengthTicks : null;
        float elapsed = world - started;
        if (elapsed <= clip.lengthTicks) return elapsed;
        return clip.loop == AttachmentAnimation.Loop.HOLD ? clip.lengthTicks : null;
    }

    /**
     * Each triggered clip's channels now, summed the way they're drawn, or the idle due when nothing is
     * playing. A one-shot's start is only known where it's been watched for ({@link #tickTimedClips}).
     */
    static void sampleClips(LivingEntity entity, AttachmentDef def, BiFunction<String, String, AttachmentAnimation.Clip> clips,
                            Map<String, float[]> rotations, Map<String, float[]> positions, Map<String, float[]> scales) {
        if (def.animations().isEmpty()) return;
        long world = entity.level().getGameTime();
        Map<Integer, Long> started = TIMED.getOrDefault(entity, Map.of()).getOrDefault(def.id(), Map.of());
        float[] work = new float[3];
        boolean anyActive = false;
        List<AttachmentDef.AnimationEntry> entries = def.animations();
        for (int i = 0; i < entries.size(); i++) {
            AttachmentDef.AnimationEntry entry = entries.get(i);
            if (entry.idle() || !active(entity, entry.state(), entry.conditionJson())) continue;
            AttachmentAnimation.Clip clip = clips.apply(entry.animSha(), entry.clip());
            if (clip == null) continue;
            Float time = clipTime(clip, world, started.get(i));
            if (time == null) continue;
            anyActive = true;
            sample(clip, time, work, rotations, positions, scales);
        }
        if (anyActive) return;
        IdlePick pick = idlePick(idleSeed(entity, def), entries, clips, world);
        if (pick == null) return;
        AttachmentAnimation.Clip clip = clips.apply(entries.get(pick.entry()).animSha(), entries.get(pick.entry()).clip());
        if (clip != null) sample(clip, world - pick.start(), work, rotations, positions, scales);
    }

    private static void sample(AttachmentAnimation.Clip clip, float time, float[] work, Map<String, float[]> rotations,
                               Map<String, float[]> positions, Map<String, float[]> scales) {
        for (var bone : clip.bones.entrySet()) {
            AttachmentAnimation.BoneTrack track = bone.getValue();
            if (track.rotation() != null) add(rotations, bone.getKey(), track.rotation().sample(time, work));
            if (track.position() != null) add(positions, bone.getKey(), track.position().sample(time, work));
            if (track.scale() != null) {
                float[] value = track.scale().sample(time, work);
                float[] slot = scales.computeIfAbsent(bone.getKey(), k -> new float[]{1f, 1f, 1f});
                for (int i = 0; i < 3; i++) slot[i] *= value[i];
            }
        }
    }

    /**
     * Runs each server tick for a person: notes when a clip with a set length starts, for the few attachments
     * that have one. Everyone else costs a look at a cached answer.
     */
    public static void tickTimedClips(LivingEntity entity, List<AttachmentDef> worn, BiFunction<String, String, AttachmentAnimation.Clip> clips) {
        long world = entity.level().getGameTime();
        for (AttachmentDef def : worn) {
            if (def.animations().isEmpty()) continue;
            List<AttachmentDef.AnimationEntry> entries = def.animations();
            for (int i = 0; i < entries.size(); i++) {
                AttachmentDef.AnimationEntry entry = entries.get(i);
                if (entry.idle()) continue;
                AttachmentAnimation.Clip clip = clips.apply(entry.animSha(), entry.clip());
                if (clip == null || clip.loop == AttachmentAnimation.Loop.LOOP) continue;
                Map<Integer, Long> started = TIMED.computeIfAbsent(entity, k -> new HashMap<>())
                        .computeIfAbsent(def.id(), k -> new HashMap<>());
                if (active(entity, entry.state(), entry.conditionJson())) started.putIfAbsent(i, world);
                else started.remove(i);
            }
        }
    }

    /** An idle clip that's playing: which entry, and the world tick it began. */
    public record IdlePick(int entry, long start) {}

    public static long idleSeed(LivingEntity entity, AttachmentDef def) {
        return entity.getUUID().getLeastSignificantBits() * 31L + def.id().hashCode();
    }

    /**
     * The idle clip due at this world tick, the same on every side: each stretch of time rolls one pick
     * from the seed and the clock instead of a random number. A pick respects its entry's cooldown by
     * giving way if the same entry came up that recently.
     */
    @Nullable
    public static IdlePick idlePick(long seed, List<AttachmentDef.AnimationEntry> entries,
                                    BiFunction<String, String, AttachmentAnimation.Clip> clips, long world) {
        float total = 0f;
        for (AttachmentDef.AnimationEntry entry : entries) {
            if (entry.idle()) total += Math.max(0f, entry.weight());
        }
        if (total <= 0f) return null;
        long window = Math.floorDiv(world, IDLE_WINDOW);
        for (long back = 0; back <= IDLE_LOOKBACK; back++) {
            long at = window - back;
            int entry = rolled(seed, entries, total, at);
            if (entry < 0) continue;
            int cooldownWindows = (int) Math.min(IDLE_MAX_COOLDOWN, (entries.get(entry).cooldownTicks() + IDLE_WINDOW - 1) / IDLE_WINDOW);
            boolean cooling = false;
            for (int j = 1; j <= cooldownWindows && !cooling; j++) cooling = rolled(seed, entries, total, at - j) == entry;
            if (cooling) continue;
            long start = at * IDLE_WINDOW + Math.floorMod(mix(seed, at * 7L + 3L), IDLE_WINDOW / 2);
            AttachmentAnimation.Clip clip = clips.apply(entries.get(entry).animSha(), entries.get(entry).clip());
            if (clip != null && world >= start && world - start <= clip.lengthTicks) return new IdlePick(entry, start);
        }
        return null;
    }

    private static int rolled(long seed, List<AttachmentDef.AnimationEntry> entries, float total, long window) {
        float pick = (Math.floorMod(mix(seed, window), 1_000_000L) / 1_000_000f) * total;
        for (int i = 0; i < entries.size(); i++) {
            AttachmentDef.AnimationEntry entry = entries.get(i);
            if (!entry.idle()) continue;
            pick -= Math.max(0f, entry.weight());
            if (pick <= 0f) return i;
        }
        return -1;
    }

    private static long mix(long seed, long value) {
        long z = seed + value * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * What a rig's own poses and clips do to its bones now, for a body the server can't draw: six floats a
     * bone, a rotation in radians then an offset in pixels, in Java model space.
     */
    static Map<String, float[]> rigBones(LivingEntity entity, RigDefinition rig, Function<String, Map<String, AttachmentAnimation.Clip>> animations) {
        Map<String, float[]> out = new HashMap<>();
        addPose(out, rig.poseBones("neutral"));
        if (entity.isCrouching()) addPose(out, rig.poseBones("crouch"));
        if (entity.isSleeping()) addPose(out, rig.poseBones("sleep"));
        RigDefinition.Animation animation = rig.animation();
        if (animation == null) return out;
        for (RigDefinition.AnimationRule rule : animation.rules()) {
            if (!rule.state().isEmpty() && !stateActive(entity, rule.state())) continue;
            if (!rule.whenJson().isEmpty() && !conditionHolds(entity, rule.whenJson())) continue;
            addClip(out, rule, animation, animations, entity.level().getGameTime());
            break;
        }
        return out;
    }

    private static void addPose(Map<String, float[]> out, @Nullable List<RigDefinition.PoseBone> bones) {
        if (bones == null) return;
        for (RigDefinition.PoseBone bone : bones) {
            float[] slot = out.computeIfAbsent(bone.bone(), k -> new float[6]);
            for (int i = 0; i < 3; i++) {
                slot[i] += (float) Math.toRadians(bone.rotation()[i]);
                slot[i + 3] += bone.offset()[i];
            }
        }
    }

    private static void addClip(Map<String, float[]> out, RigDefinition.AnimationRule rule, RigDefinition.Animation animation,
                                Function<String, Map<String, AttachmentAnimation.Clip>> animations, long world) {
        String name = rule.clip();
        int separator = name.indexOf('#');
        Map<String, AttachmentAnimation.Clip> clips = animations.apply(separator > 0 ? name.substring(0, separator) : animation.file());
        if (separator > 0) name = name.substring(separator + 1);
        if (clips == null) return;
        AttachmentAnimation.Clip clip = clips.get(name);
        if (clip == null) {
            for (var entry : clips.entrySet()) {
                if (entry.getKey().endsWith("." + name)) clip = entry.getValue();
            }
        }
        if (clip == null || clip.lengthTicks <= 0f) return;
        float time = clip.loop == AttachmentAnimation.Loop.LOOP ? world * rule.speed() % clip.lengthTicks : clip.lengthTicks;
        float[] work = new float[3];
        for (var bone : clip.bones.entrySet()) {
            AttachmentAnimation.BoneTrack track = bone.getValue();
            float[] slot = out.computeIfAbsent(bone.getKey(), k -> new float[6]);
            if (track.rotation() != null) {
                float[] degrees = track.rotation().sample(time, work);
                slot[0] += (float) Math.toRadians(degrees[0]);
                slot[1] += (float) Math.toRadians(-degrees[1]);
                slot[2] += (float) Math.toRadians(-degrees[2]);
            }
            if (track.position() != null) {
                float[] pixels = track.position().sample(time, work);
                slot[3] += pixels[0];
                slot[4] -= pixels[1];
                slot[5] += pixels[2];
            }
        }
    }

    private static void add(Map<String, float[]> map, String bone, float[] value) {
        float[] slot = map.computeIfAbsent(bone, k -> new float[3]);
        for (int i = 0; i < 3; i++) slot[i] += value[i];
    }
}
