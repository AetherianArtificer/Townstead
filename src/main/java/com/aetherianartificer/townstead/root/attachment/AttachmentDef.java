package com.aetherianartificer.townstead.root.attachment;

import org.jetbrains.annotations.Nullable;

/**
 * The synced form of an attachment: render placement plus the SHA-1 hashes of its
 * geometry and texture blobs (the bytes are content-addressed and pulled/cached
 * separately). Authored as {@code data/<ns>/attachment/<id>.json}; the server
 * resolves the geometry/texture file references to hashes and syncs this view.
 *
 * <p>Targeting precedence: {@code targetTag} (every point carrying the tag, so one
 * "ears" attachment fits any rig that exposes an {@code ear} point) -> {@code targetPoint}
 * (one named point) -> {@code bone} (anchor straight to a model bone).</p>
 *
 * <p>{@code tintSource} draws the tint from the bearer instead of the flat
 * {@code tint}: resolved skin tone, rendered hair colour, or expressed eye
 * colour (author the texture in grayscale for these). {@code tintBlend} is how
 * the tint applies (multiply / screen / overlay / color — non-multiply modes
 * bake a derived texture client-side) and {@code tintStrength} fades it.
 * {@code tintMaskSha1} is an optional grayscale texture gating the tint per
 * pixel (black = untinted, white = full, mid-greys = partial), so one attachment
 * can hold a tinted membrane against neutral chitin; a masked attachment always
 * takes the bake path, including {@code multiply}, which otherwise rides the
 * vertex colour. {@code emissiveSha1} is an optional second texture drawn full-bright over the
 * same geometry (glowing runes, foxfire tips); {@code translucent} renders the
 * whole attachment with alpha (wisps, membranes). Each {@code morph} channel reads one size
 * value rolled on the gene that granted this attachment: a channel with
 * {@code axes} scales its bones about their own pivots (each axis weighted;
 * 1 = follows the value fully, 0 = unaffected — no bones scales the whole
 * attachment at its anchor), and a channel with {@code rotate} turns its bones
 * by {@code rotate × value} degrees (a droop reads organic where a shrink reads
 * cheap). The single-channel shorthand maps to one anonymous channel.
 * {@code visibility} rules gate bones on channel values — a rounded human ear
 * tip shown only below a length threshold makes a quarter-elf read human rather
 * than miniature-elven.</p>
 *
 * <p>{@code whenJson} is an optional pheno condition gating the whole attachment:
 * it renders only while the condition holds for the bearer, evaluated client-side
 * like pose conditions (a beard worn only by masculine villagers). Empty = always.</p>
 *
 * <p>{@code hidesUnder} lists armor slots ({@code helmet}/{@code chestplate}/
 * {@code leggings}/{@code boots}) that suppress the attachment entirely while the
 * bearer wears something there (horns vanish under a helm). The gentler option is
 * a {@code wearing_<slot>} pose that folds the attachment flat instead.</p>
 *
 * <p>{@code stages} overrides render placement per canonical life stage
 * ({@code baby}/{@code toddler}/{@code child}/{@code teen}/{@code adult}/{@code senior}):
 * a scale multiplier, an extra offset, and optionally a whole different geometry
 * (baby elves get baby ears; elders can grow a longer model).</p>
 *
 * <p>{@code poses} are state-driven rotations evaluated client-side each frame:
 * an entry is active while its named state (sleeping, sprinting, ...) or its pheno
 * condition holds, and its rotations ease in/out over {@code transitionTicks}.
 * Active entries apply in authored order, later ones winning per bone.</p>
 *
 * <p>{@code physics} chains give bones secondary motion: each chain is a root-to-tip
 * bone run simulated client-side as world-space particles springing toward the
 * animated pose, so anchor motion, gravity, and any pose or clip all drive it.</p>
 *
 * <p>{@code animations} are Blockbench keyframe clips ({@code .animation.json},
 * blob-synced like geometry) triggered the same way poses are: while a named state
 * or pheno condition holds, or drawn from a {@code random_idle} pool. Clips fade
 * in/out over {@code transitionTicks} and compose between poses and physics, so a
 * keyframed swish still jiggles.</p>
 */
public record AttachmentDef(
        String id,
        String geoSha1,
        String textureSha1,
        @Nullable String targetTag,
        @Nullable String targetPoint,
        String bone,
        float[] offset,
        float[] rotation,
        float scale,
        int tint,
        int tintSource,
        int tintBlend,
        float tintStrength,
        String tintMaskSha1,
        String emissiveSha1,
        boolean translucent,
        java.util.List<String> hidesUnder,
        String whenJson,
        java.util.List<MorphChannel> morph,
        java.util.List<VisibilityRule> visibility,
        java.util.Map<String, StageOverride> stages,
        java.util.List<PoseEntry> poses,
        java.util.List<PhysicsChain> physics,
        java.util.List<AnimationEntry> animations
) {
    /**
     * {@code tintSource} values: the flat tint, or a colour resolved from the bearer.
     * {@code TINT_GENE} reads the {@code tint_r}/{@code tint_g}/{@code tint_b} channels
     * rolled on the granting gene's allele (a heritable individual colour). {@code TINT_COAT} is the
     * bearer's coat (its expressed skin-tone gene) when it has one, else its hair: a cat's ears match
     * its fur, a half-cat's with a human skin match its hair.
     */
    public static final int TINT_FLAT = 0, TINT_SKIN = 1, TINT_HAIR = 2, TINT_EYES = 3, TINT_GENE = 4, TINT_COAT = 5;

    /** The named pose states the client can resolve without extra sync. */
    public static final java.util.List<String> POSE_STATES = java.util.List.of(
            "sleeping", "sitting", "sprinting", "sneaking", "swimming", "gliding",
            "parachuting", "moving", "hurt", "attacking", "panicking", "working",
            "wearing_helmet", "wearing_chestplate", "wearing_leggings", "wearing_boots");

    /** The armor-slot names {@code hidesUnder} (and the {@code wearing_*} poses) accept. */
    public static final java.util.List<String> EQUIPMENT_SLOTS = java.util.List.of(
            "helmet", "chestplate", "leggings", "boots");

    /** The vanilla slot behind an author-facing armor-slot name, or null when unknown. */
    @Nullable
    public static net.minecraft.world.entity.EquipmentSlot equipmentSlot(String name) {
        return switch (name) {
            case "helmet" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
            case "chestplate" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
            case "leggings" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
            case "boots" -> net.minecraft.world.entity.EquipmentSlot.FEET;
            default -> null;
        };
    }

    /** One life stage's render override: scale multiplier, extra offset, optional model swap. */
    public record StageOverride(float scale, float[] offset, @Nullable String geoSha1) {}

    /**
     * One morph channel: the size-channel {@code name} it reads ({@code ""} = the
     * legacy anonymous roll), the bones it drives, and what it does — {@code axes}
     * scales (empty bones = whole attachment), {@code rotate} turns by
     * {@code rotate × value} degrees (geo convention). Either may be null.
     */
    public record MorphChannel(String channel, java.util.List<String> bones,
                               float @Nullable [] axes, float @Nullable [] rotate) {}

    /**
     * One visibility gate: the named bones render only while the channel's value is
     * under {@code below} and over {@code above} (either bound may be {@code NaN} =
     * unbounded). A bearer with no rolled value for the channel keeps the bones visible.
     */
    public record VisibilityRule(java.util.List<String> bones, String channel, float below, float above) {}

    /**
     * One state pose: active while {@code state} (a named client-resolvable state) or
     * {@code conditionJson} (a serialized pheno condition, evaluated on the client
     * entity) holds — exactly one of the two is non-empty. {@code rotation} turns the
     * whole attachment at its anchor (ZYX degrees, def convention); {@code boneRotations}
     * turn named geometry bones (degrees, in the .geo.json's own convention so authors
     * can copy angles straight from Blockbench). {@code transitionTicks} is the ease
     * time constant in and out.
     */
    public record PoseEntry(String state, String conditionJson, float @Nullable [] rotation,
                            java.util.Map<String, float[]> boneRotations, float transitionTicks) {}

    /**
     * One keyframe-animation trigger. Like {@link PoseEntry}, exactly one of
     * {@code state} / {@code conditionJson} is non-empty — unless {@code idle} is
     * set, which marks a {@code random_idle} pool entry (played once at random
     * while nothing else is animating, weighted by {@code weight}, then quiet for
     * {@code cooldownTicks}). {@code animSha} is the synced {@code .animation.json}
     * blob and {@code clip} the animation name inside it (empty = the file's only
     * clip). {@code transitionTicks} is the fade in/out time.
     */
    public record AnimationEntry(String state, String conditionJson, String animSha, String clip,
                                 float transitionTicks, float weight, int cooldownTicks, boolean idle) {}

    /**
     * One physics chain, {@code bones} ordered root to tip, fully resolved (defaults,
     * then {@code preset}, then converted legacy keys, then the chain's own keys).
     * Each bone's tip is a world-space particle that springs toward the bone's animated
     * pose: {@code frequency} (Hz) is how fast it returns, {@code dampingRatio} how much
     * it overshoots (below 1 bounces, 1 settles without overshoot), {@code gravity} how
     * far each bone droops toward world-down (0 none, 1 heavy), {@code inertia} how much of the
     * bearer's motion it feels (1 all, 0 none), {@code drag} the air resistance that
     * streams it back while moving, and {@code limit} (degrees) the largest swing of
     * each bone away from where its parent points. Every knob takes a root-to-tip ramp.
     *
     * <p>{@code hinge} ("pitch", "side", or "none") keeps the swing in one plane;
     * {@code collide} names the rig colliders it rests against ({@code ["*"]}, the default, is all of them); {@code restPitch} (degrees) tilts the rest
     * pose; {@code sway} (degrees) and {@code swaySpeed} add idle motion;
     * {@code scaleWithSize} speeds small bearers up and slows big ones down.
     * {@code notes} lists legacy keys found at load (server-side only, never synced).</p>
     *
     * <p>{@code segments} > 1 with a single listed bone slices that bone's geometry
     * into that many chained virtual segments at bake time (along {@code axis}, or
     * the longest axis when {@code "auto"}) — bendy-lib-style bending for tails that
     * weren't authored as chains.</p>
     */
    public record PhysicsChain(java.util.List<String> bones, String preset,
                               Knob frequency, Knob dampingRatio, Knob gravity, Knob inertia,
                               Knob drag, Knob limit, String hinge, java.util.List<String> collide,
                               float restPitch, float sway, float swaySpeed, boolean scaleWithSize,
                               int segments, String axis, java.util.List<String> notes) {

        /** The bone names the sim actually runs over: the authored list, or the generated segment names. */
        public java.util.List<String> effectiveBones() {
            if (segments <= 1 || bones.size() != 1) return bones;
            java.util.List<String> out = new java.util.ArrayList<>(segments);
            out.add(bones.get(0));
            for (int i = 2; i <= segments; i++) out.add(bones.get(0) + "__seg" + i);
            return out;
        }

        public PhysicsChain withNotes(java.util.List<String> newNotes) {
            return new PhysicsChain(bones, preset, frequency, dampingRatio, gravity, inertia, drag, limit,
                    hinge, collide, restPitch, sway, swaySpeed, scaleWithSize, segments, axis, newNotes);
        }
    }

    /** A physics knob ramped from the chain's root joint to its tip. */
    public record Knob(float root, float tip) {
        public static Knob of(float value) {
            return new Knob(value, value);
        }

        /** The value at {@code t} along the chain (0 = root, 1 = tip). */
        public float at(float t) {
            return root + (tip - root) * t;
        }

        public boolean uniform() {
            return root == tip;
        }
    }
}
