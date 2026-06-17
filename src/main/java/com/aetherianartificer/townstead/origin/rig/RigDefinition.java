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
        // Where back-worn render layers (backpack, cape, elytra) sit, or null. These layers anchor to
        // the host humanoid body bone; for a non-humanoid rig that bone is invisible and stuck at the
        // default humanoid chest, so the item floats. This re-poses that bone onto the rig's actual
        // back. Only consulted for non-humanoid rigs (a humanoid rig drives the bone from the rig).
        Back back,
        // Where head-worn render layers (helmet, worn mob head/pumpkin) sit, or null. They anchor to
        // the host head bone, which for a non-humanoid rig floats at the default humanoid head; this
        // re-poses it onto the rig's real head. Only consulted for non-humanoid rigs.
        Adjust head,
        // Worn boots placed on a non-humanoid rig's legs: one entry per leg bone (vanilla only draws
        // two boots). Empty when the rig declares none.
        List<Boot> boots,
        // Whether this rig uses MCA hair. False by default (a custom rig draws its own head and a
        // skeleton has no hair, so the editor hides the hair controls); set true to opt back in.
        boolean hair
) {
    public enum ModelType { ENTITY_LAYER, GEOMETRY }

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
     * The anchor for back-worn render layers (backpack, cape, elytra), which all read the host body
     * bone. {@code base} re-poses that bone onto the rig's real back; {@code items} holds optional
     * per-layer deltas (keyed {@code backpack}/{@code cape}/{@code elytra}) for items that need a
     * slightly different placement. Tuned per rig by eye.
     */
    public record Back(Adjust base, Map<String, Adjust> items) {
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
