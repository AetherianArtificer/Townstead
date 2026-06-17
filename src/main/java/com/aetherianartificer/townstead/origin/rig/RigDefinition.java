package com.aetherianartificer.townstead.origin.rig;

import java.util.List;
import java.util.Map;

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
        // Whether this rig uses MCA hair. False by default (a custom rig draws its own head and a
        // skeleton has no hair, so the editor hides the hair controls); set true to opt back in.
        boolean hair
) {
    public enum ModelType { ENTITY_LAYER, GEOMETRY }

    public enum ArmorType { NONE, LAYERS, CUSTOM }

    /**
     * The face overlay anchor for {@code SpeciesFace}: which {@code bone} the face rides, the
     * {@code center} of the face plane on that bone and its {@code size} (both in model pixels), and
     * {@code forward} — the sign of the axis the face points along ({@code -1} = the bone's -Z front,
     * {@code +1} = +Z). The eyes/mouth quads are built from center ± size/2 in the bone's local space.
     */
    public record Face(String bone, float[] center, float[] size, float forward) {}

    /** The animation channels every rig is addressed by; the bone map names a bone for each. */
    public static final List<String> CHANNELS =
            List.of("head", "headwear", "body", "right_arm", "left_arm", "right_leg", "left_leg");

    /** The author bone name for an animation channel, defaulting to the vanilla humanoid name. */
    public String boneFor(String channel) {
        String bone = bones.get(channel);
        if (bone != null) return bone;
        return channel.equals("headwear") ? "hat" : channel;
    }
}
