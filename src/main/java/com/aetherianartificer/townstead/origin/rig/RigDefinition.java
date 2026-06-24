package com.aetherianartificer.townstead.origin.rig;

import com.aetherianartificer.townstead.origin.Hold;
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
        // Per-state bone poses (e.g. "crouch"): each state names bones to rotate/offset on top of the
        // model's own setupAnim, so a pack authors a rig's crouch (a spider splays its legs) as data
        // instead of engine code. Empty when the rig declares none.
        Map<String, List<PoseBone>> poses,
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
        @Nullable String cameraBone
) {
    public enum ModelType { ENTITY_LAYER, GEOMETRY }

    /** The collision/interaction box a rig imposes, in blocks (width is the square footprint side). */
    public record Hitbox(float width, float height) {}

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
