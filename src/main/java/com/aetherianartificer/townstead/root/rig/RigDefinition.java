package com.aetherianartificer.townstead.root.rig;

import com.aetherianartificer.townstead.root.Hold;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A data-pack rig: the body model a species renders as, its texture, the bone that plays each
 * animation role, and how it wears armor. Loaded server-side from {@code data/<ns>/rigs/*.json} (see
 * {@link RigJsonLoader}), synced to clients with the origin catalog, and referenced by id from a
 * species' {@code rig.base}. Lives in {@code data/} alongside species/genes so a pack author writes
 * all of a body in one place; rendering reads it from the synced client copy.
 *
 * <p>The {@code bones} map is what lets a custom rig use arbitrary bone names: each animation channel
 * ({@code head, headwear, body, right_arm, left_arm, right_leg, left_leg}) maps to the author's bone
 * name, so the animation bridge, held items, and armor address the right part. Vanilla bodies use the
 * identity map (channel name == bone name, with {@code headwear -> hat}).</p>
 */
public record RigDefinition(
        String id,
        ModelType modelType,
        // For ENTITY_LAYER: the model layer location, split into ref ("minecraft:skeleton") + layer
        // ("main"). For GEOMETRY: ref is the geometry file path (loaded in a later phase).
        String modelRef,
        String modelLayer,
        String texture,
        Map<String, String> bones,
        ArmorType armorType,
        // "ns:path#layer" model-layer references for the inner/outer armor, or null when armorType
        // is not LAYERS.
        String armorInner,
        String armorOuter,
        // Where a custom face (eyes/mouth overlay) sits on this rig, or null when the rig has no face.
        // Data-driven so any body (humanoid, spider, custom geo) places its face where it belongs,
        // instead of assuming a vanilla 8×8 humanoid head.
        Face face,
        // Where back-worn render layers (backpack, cape, elytra) sit, or null. These layers anchor to
        // the host humanoid body bone; for a non-humanoid rig that bone is invisible and stuck at the
        // default humanoid chest, so the item floats. This re-poses that bone onto the rig's actual
        // back. Only consulted for non-humanoid rigs (a humanoid rig drives the bone from the rig).
        WornAnchor back,
        // Where head-worn render layers (helmet, worn mob head/pumpkin) AND Townstead head wearables (the
        // scarf) sit, or null. The {@code base} re-poses the host head bone onto a non-humanoid rig's real
        // head (helmets); {@code items} holds per-item placement deltas keyed by item id (e.g.
        // {@code townstead:scarf}) that the worn-item layer applies on top, so a scarf sits as a collar
        // rather than where a helmet sits.
        WornAnchor head,
        // Worn boots placed on a non-humanoid rig's legs: one entry per leg bone (vanilla only draws
        // two boots). Empty when the rig declares none.
        List<Boot> boots,
        // Which bone each hand grips a held item from (e.g. a front leg), with per-hand third-person and
        // first-person nudges. A rig property, not a soul one: the grip is a function of the body's bones,
        // so it lives here next to the other bone-anchored wearables. Hold.NONE when the rig can't hold.
        Hold hold,
        // Whether this rig uses MCA hair. False by default (a custom rig draws its own head and a
        // skeleton has no hair, so the editor hides the hair controls); set true to opt back in.
        boolean hair,
        // Per-state poses (e.g. "crouch", "sleep"): each state names bones to rotate/offset on top of the
        // model's own setupAnim, and optionally a whole-body transform (yaw/pitch/roll of the entity root,
        // for a turn a per-bone pose can't express on a rig whose bones aren't under a common root). So a
        // pack authors a rig's crouch (a spider splays its legs) or how it rests asleep as data instead of
        // engine code. Empty when the rig declares none.
        Map<String, PoseState> poses,
        // The collision/interaction box this rig gives the entity, in blocks, or null to keep MCA's
        // scale-derived default (0.6 x 2.0). Absolute (not gene-scaled): the number you write is the box
        // you get, so a full-block egg rig declares 1 x 1 instead of inheriting a 2-tall humanoid column.
        // Applied via the EntityEvent.Size hook for whichever rig the entity currently renders as.
        @Nullable Hitbox hitbox,
        // Vanilla equipment slots this rig refuses (head/chest/legs/feet/mainhand/offhand): a body that
        // can't wear or hold gear in a slot, e.g. an egg wears nothing. The equip backstop strips anything
        // placed in a disabled slot back to the wearer; since the slot then stays empty, nothing renders
        // there either. Empty = the rig allows every slot (the default).
        Set<EquipmentSlot> disabledSlots,
        // The bone the first-person camera sits at, so the eye height matches where this body's head
        // actually is instead of the default humanoid 1.62 (a low spider body's eyes sit near the ground).
        // The eye height is derived client-side from this bone's resting position in the baked model
        // ({@code RigCamera}). Empty = keep the height-proportional default.
        @Nullable String cameraBone,
        // How humanoid-authored Emotecraft emotes map onto a non-humanoid body: which rig bone each
        // humanoid channel drives, how its rotation axes are remapped, and which emotes the body is even
        // willing to play (a spider can wave but should not try to Cossack-dance). Null when the rig
        // expresses emotes the plain humanoid way (the default for a humanoid body); see {@link EmoteMap}.
        @Nullable EmoteMap emote
) {
    public enum ModelType { ENTITY_LAYER, GEOMETRY }

    /**
     * The collision/interaction box a rig imposes, in blocks (width is the square footprint side).
     * {@code crouchHeight} is the box while crouching; 0 derives it from {@code height} at vanilla's
     * ratio (a player's 1.8 standing becomes 1.5), so a rig only declares it to tune the squeeze.
     */
    public record Hitbox(float width, float height, float crouchHeight) {

        /** Vanilla's crouch ratio (1.5 / 1.8), used when a rig declares no explicit crouch height. */
        private static final float CROUCH_RATIO = 1.5f / 1.8f;

        public float crouchedHeight() {
            return crouchHeight > 0f ? crouchHeight : height * CROUCH_RATIO;
        }
    }

    /**
     * One named pose: an optional whole-body {@code body} transform (entity-root yaw/pitch/roll, applied
     * by the render mixin for states that hook it, e.g. sleep) plus the per-bone {@code bones} list. A
     * state authored as a bare JSON array has a null body (bones only); an object form carries both.
     */
    public record PoseState(@Nullable BodyPose body, List<PoseBone> bones) {}

    /**
     * A whole-body pose transform. {@code yaw}/{@code pitch}/{@code roll} are degrees added to the entity
     * root, always in the same frame so they read alike across states: {@code sleep} replaces vanilla's
     * lay-down with them, {@code crawl} adds them on top of the upright stance. {@code offset} is model
     * pixels applied uniformly to the whole rendered body (all bones together, in the model frame, so it
     * reads the same way and sign as a per-bone offset). Not per-bone: it shifts the entire body as one.
     */
    public record BodyPose(float yaw, float pitch, float roll, float[] offset) {}

    /**
     * One bone in a named pose: the {@code bone}'s geo name, an additive {@code rotation} in degrees
     * (X/Y/Z) and {@code offset} in model pixels, applied on top of the model's already-posed bones.
     */
    public record PoseBone(String bone, float[] rotation, float[] offset) {}

    public enum ArmorType { NONE, LAYERS, CUSTOM }

    /**
     * A transform applied to the host body bone: {@code offset} in model pixels, {@code rotation} in
     * degrees (X/Y/Z). The base anchor places back-worn layers on the rig; per-item entries add a
     * delta on top of it.
     */
    public record Adjust(float[] offset, float[] rotation) {
        public static final Adjust ZERO = new Adjust(new float[]{0f, 0f, 0f}, new float[]{0f, 0f, 0f});

        /** This adjust plus a per-item delta (component-wise; rotation summed per axis in degrees). */
        public Adjust plus(Adjust d) {
            return new Adjust(
                    new float[]{offset[0] + d.offset[0], offset[1] + d.offset[1], offset[2] + d.offset[2]},
                    new float[]{rotation[0] + d.rotation[0], rotation[1] + d.rotation[1], rotation[2] + d.rotation[2]});
        }
    }

    /**
     * A worn-anchor on a non-humanoid rig: {@code base} re-poses the host body/head bone onto the rig's
     * real back/head (so vanilla back/head layers land on it), and {@code items} holds optional per-item
     * placement deltas keyed by item id ({@code backpack}/{@code cape}/{@code elytra} for back;
     * {@code townstead:scarf} for head). Tuned per rig by eye.
     */
    public record WornAnchor(Adjust base, Map<String, Adjust> items) {
        /** The transform for a layer key: base plus its delta, or just base when none is declared. */
        public Adjust forItem(String key) {
            Adjust delta = items.get(key);
            return delta == null ? base : base.plus(delta);
        }
    }

    /**
     * One worn boot placed on a rig leg: anchored to {@code bone} (so it tracks the walk), drawn as
     * the {@code left} or right boot mesh, {@code scale}d, with a {@code seat} ({@code offset} in the
     * bone's local frame + {@code rotation}) tuned per leg to sit it on that foot.
     */
    public record Boot(String bone, boolean left, float scale, Adjust seat) {}

    /**
     * The face overlay anchor for {@code SpeciesFace}: which {@code bone} the face rides, the
     * {@code center} of the face plane on that bone and its {@code size} (both in model pixels), and
     * {@code forward} — the sign of the axis the face points along ({@code -1} = the bone's -Z front,
     * {@code +1} = +Z). The eyes/mouth quads are built from center ± size/2 in the bone's local space.
     */
    public record Face(String bone, float[] center, float[] size, float forward) {}

    /** How a remapped emote rotation is blended onto its target bone. */
    public enum EmoteMode {
        /** Lerp the bone from its current (gait) pose toward the emote pose. For a homologous bone. */
        ABSOLUTE,
        /** Add the emote's delta on top of whatever the gait left on the bone. For a leg posing as an arm. */
        ADDITIVE
    }

    /**
     * One humanoid emote channel ({@code right_arm}, {@code head}, ...) remapped onto a rig bone.
     *
     * <p>The emote's sampled rotation {@code (x,y,z)} is rebased into the bone's frame as
     * {@code out[i] = sign[i] * gain[i] * src[perm[i]] + euler[i]}, then clamped to
     * {@code [clampMin[i], clampMax[i]]}. {@code perm}/{@code sign} are the signed-axis permutation
     * (JSON {@code axis:["z","x","-y"]}); {@code euler} is an arbitrary post-offset in radians; both
     * remap forms compose so a pack can align a 90-degree leg and still nudge an odd rest angle.</p>
     *
     * <p>{@code also} fans the same remapped motion onto extra bones (each with its own gain) so a
     * single human arm reads as a wave across several legs.</p>
     *
     * <p>{@code bend} opts this channel's bone into playerAnim's segment bend (the curve a humanoid
     * limb gets at the elbow/knee), scaled by {@code bendGain}; off by default since bend is authored
     * for jointed humanoid limbs and only makes sense on bones meant to flex.</p>
     */
    public record EmoteChannel(
            String bone,
            EmoteMode mode,
            int[] axisPerm,     // length 3; source axis index (0=x,1=y,2=z) feeding each output axis
            float[] axisSign,   // length 3; sign multiplier per output axis
            float[] euler,      // length 3; post-offset in radians
            float[] gain,       // length 3; per-output-axis multiplier
            boolean translation,
            float[] clampMin,   // length 3, radians (NEGATIVE_INFINITY = no lower bound)
            float[] clampMax,   // length 3, radians (POSITIVE_INFINITY = no upper bound)
            List<EmoteFan> also,
            boolean bend,       // apply the emote's segment bend to this bone
            float bendGain      // multiplier on the bend angle
    ) {}

    /** A follower bone driven by a parent channel's remapped motion (added on top) with its own gain. */
    public record EmoteFan(String bone, float[] gain) {}

    /**
     * Which emotes a rig is willing to play. An emote is refused when it's in {@code deny}, or when the
     * share of its motion energy that lands in expressible channels is below {@code minCoverage} (and it
     * isn't in {@code allow}). A refused emote substitutes {@code fallback} if one is set. {@code allow}/
     * {@code deny} match an emote's path or display name, case-insensitively.
     */
    public record EmotePolicy(float minCoverage, String fallback, Set<String> allow, Set<String> deny) {
        public static final EmotePolicy NONE = new EmotePolicy(0f, "", Set.of(), Set.of());
    }

    /**
     * How much of an emote's whole-entity body transform (the lift/drop authored in block units for a
     * 2-tall humanoid) this body takes. {@code scale} multiplies the translation and rotation
     * (0 = none, the old {@code body_motion:false}; 1 = full, the old {@code true}); {@code floor} clamps
     * how far below the entity's feet the body may drop (blocks, negative = down; NEGATIVE_INFINITY = no
     * clamp). Lets a low body keep small intentional dips without a humanoid-scale "sit" sinking it under
     * the floor.
     */
    public record BodyMotion(float scale, float floor) {
        public static final BodyMotion OFF = new BodyMotion(0f, Float.NEGATIVE_INFINITY);
        public static final BodyMotion FULL = new BodyMotion(1f, Float.NEGATIVE_INFINITY);

        /** False when the body transform is fully suppressed (scale 0). */
        public boolean active() {
            return scale != 0f;
        }

        /** True when this imposes a limit beyond what a full humanoid transform would do. */
        public boolean limited() {
            return scale != 1f || floor > Float.NEGATIVE_INFINITY;
        }

        /** The clamped, scaled Y translation (blocks): scale applied, then never below {@code floor}. */
        public float clampY(float rawY) {
            return Math.max(rawY * scale, floor);
        }
    }

    /**
     * The rig's emote behaviour: {@code bodyMotion} scales/clamps the whole-entity lift/drop (so a wide
     * low body neither tumbles nor sinks), {@code channels} maps each expressible humanoid channel to a
     * bone (a channel absent from the map is NOT expressible), and {@code policy} decides which emotes are
     * allowed at all.
     */
    public record EmoteMap(BodyMotion bodyMotion, Map<String, EmoteChannel> channels, EmotePolicy policy) {
        /** Whether this rig can express the given humanoid channel (it names a bone for it). */
        public boolean expresses(String channel) {
            return channels.containsKey(channel);
        }
    }

    /** The animation channels every rig is addressed by; the bone map names a bone for each. */
    public static final List<String> CHANNELS =
            List.of("head", "headwear", "body", "right_arm", "left_arm", "right_leg", "left_leg");

    /** The author bone name for an animation channel, defaulting to the vanilla humanoid name. */
    public String boneFor(String channel) {
        String bone = bones.get(channel);
        if (bone != null) return bone;
        return channel.equals("headwear") ? "hat" : channel;
    }

    /** The per-bone list for a pose state, or null when the rig declares no such state. */
    @Nullable
    public List<PoseBone> poseBones(String state) {
        PoseState s = poses.get(state);
        return s == null ? null : s.bones();
    }

    /** The whole-body transform for a pose state, or null when the state (or rig) declares none. */
    @Nullable
    public BodyPose bodyPose(String state) {
        PoseState s = poses.get(state);
        return s == null ? null : s.body();
    }
}
