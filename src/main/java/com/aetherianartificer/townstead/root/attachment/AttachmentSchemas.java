package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.pheno.lang.schema.NodeSchema;
import com.aetherianartificer.townstead.pheno.lang.schema.PhenoType;
import com.aetherianartificer.townstead.pheno.lang.validate.NodeDomain;

import static com.aetherianartificer.townstead.pheno.lang.schema.FieldSchema.of;
import static com.aetherianartificer.townstead.pheno.lang.schema.FieldSchema.required;

/**
 * Field schemas for the attachment data files, validated through the shared schema/diagnostics
 * engine so a malformed file is a located error rather than a silent skip. Plain data records
 * (no behavior nodes), so they sit in the {@link NodeDomain#DATA} domain and are validated by
 * {@code PhenoValidator.validateData} without any behavior-tree descent.
 */
public final class AttachmentSchemas {

    private AttachmentSchemas() {}

    public static final NodeSchema ATTACHMENT = NodeSchema.of("townstead:attachment", NodeDomain.DATA)
            .doc("A cosmetic geometry attached to a body (tail, tusk, ears).")
            .field(of("schema", PhenoType.STRING).doc("Schema tag: townstead:attachment/v1."))
            .field(required("geometry", PhenoType.STRING).doc("Reference to a .geo.json under attachment/geo/."))
            .field(required("texture", PhenoType.STRING).doc("Reference to a .png under attachment/textures/."))
            .field(of("target", PhenoType.OBJECT).doc("{ tag } (every matching point) or { point } (one point)."))
            .field(of("bone", PhenoType.STRING).doc("Direct bone anchor when there is no target."))
            .field(of("offset", PhenoType.FLOAT).asList())
            .field(of("rotation", PhenoType.FLOAT).asList())
            .field(of("scale", PhenoType.FLOAT))
            .field(of("tint", PhenoType.STRING)
                    .doc("A flat #RRGGBB, or a bearer-resolved source: skin | hair | eyes | gene "
                            + "(author the texture in grayscale for sources; gene reads the heritable "
                            + "tint_r/tint_g/tint_b channels the granting gene's tint palette rolls)."))
            .field(of("skin_tint", PhenoType.BOOL)
                    .doc("Legacy alias for tint: \"skin\"."))
            .field(of("tint_blend", PhenoType.STRING)
                    .doc("How the tint applies: multiply (default) | screen | overlay | color. "
                            + "Non-multiply modes bake a derived texture client-side."))
            .field(of("tint_strength", PhenoType.FLOAT)
                    .doc("0..1 fade of the tint toward the untinted texture (default 1)."))
            .field(of("tint_mask", PhenoType.STRING)
                    .doc("Optional grayscale texture under attachment/textures/ gating the tint per "
                            + "pixel: black leaves the base colour, white takes the full tint, mid-greys "
                            + "ease between (transparent reads as black). Lets one attachment hold a "
                            + "tinted membrane against neutral chitin. Must match the base texture's "
                            + "dimensions; a masked attachment always bakes, including multiply."))
            .field(of("emissive", PhenoType.STRING)
                    .doc("Optional second texture under attachment/textures/ drawn full-bright "
                            + "over the same geometry (glowing runes, foxfire tips)."))
            .field(of("render", PhenoType.STRING)
                    .doc("cutout (default) | translucent — translucent respects the texture's alpha "
                            + "for wisps, membranes, ghost tails."))
            .field(of("hides_under", PhenoType.STRING).asList()
                    .doc("Armor slots (helmet/chestplate/leggings/boots) that hide the attachment "
                            + "entirely while worn. For a softer reaction author a wearing_<slot> pose "
                            + "instead (ears fold beneath a helm)."))
            .field(of("when", PhenoType.OBJECT)
                    .doc("Optional pheno condition gating the whole attachment: renders only while it "
                            + "holds for the bearer, evaluated client-side like pose conditions "
                            + "(a beard only masculine villagers wear). Omit = always."))
            .field(of("morph", PhenoType.OBJECT)
                    .doc("Size-value morphs from the granting gene's rolls. Shorthand { axes, bones } reads "
                            + "the single anonymous channel; { channels: { <name>: { bones, axes, rotate } } } "
                            + "reads named channels — `axes` scales per axis (no bones = whole attachment), "
                            + "`rotate` turns bones by rotate x value degrees."))
            .field(of("visibility", PhenoType.OBJECT).asList()
                    .doc("[ { bones, channel, below, above } ]: the bones render only while the channel's "
                            + "rolled value is under `below` and over `above` (either bound optional) — "
                            + "threshold shape swaps like a human ear tip below elven length."))
            .field(of("stages", PhenoType.OBJECT)
                    .doc("Per-life-stage overrides keyed baby/toddler/child/teen/adult/senior: "
                            + "{ scale, offset, geometry } — scale multiplier, extra offset, optional model swap."))
            .field(of("poses", PhenoType.OBJECT)
                    .doc("State poses: named-state keys (sleeping/sitting/sprinting/sneaking/swimming/"
                            + "moving/hurt/attacking/panicking/working/wearing_helmet/wearing_chestplate/"
                            + "wearing_leggings/wearing_boots) plus a `when` list of "
                            + "{ if: <pheno condition>, ... } entries; each entry carries { rotation, "
                            + "bones: { <bone>: { rotation } }, transition }. Evaluated client-side per frame."))
            .field(of("physics", PhenoType.OBJECT)
                    .doc("{ chains: [ { bones (root->tip), stiffness, damping, gravity, max_angle, "
                            + "min_pitch, max_pitch, rest_pitch, sway, "
                            + "follow, droop_angle, sway_speed, snap, response: { vertical, forward, lateral, turn }, "
                            + "segments, axis } ] } — client-simulated secondary motion driven by the bearer's "
                            + "movement. min_pitch/max_pitch independently limit down/up movement (negative is "
                            + "down) and default to -max_angle/+max_angle; rest_pitch shifts the neutral spring "
                            + "target before gravity droop and defaults to 0. segments > 1 with one bone slices "
                            + "its geometry into a bending chain."))
            .field(of("animations", PhenoType.OBJECT)
                    .doc("Keyframe clips from Blockbench .animation.json files "
                            + "(attachment/animations/<file>.animation.json, referenced '<file>' or '<file>#<clip>'): "
                            + "named-state keys and a `when` list trigger while active "
                            + "({ animation, transition }); `random_idle` is a weighted pool "
                            + "({ animation, weight, cooldown, transition }) played when nothing else animates. "
                            + "Clips compose between poses and physics."))
            .build();

    public static final NodeSchema ATTACHMENT_POINT = NodeSchema.of("townstead:attachment_point", NodeDomain.DATA)
            .doc("A named anchor on a rig where attachments may sit.")
            .field(of("schema", PhenoType.STRING).doc("Schema tag: townstead:attachment_point/v1."))
            .field(of("bone", PhenoType.STRING))
            .field(of("offset", PhenoType.FLOAT).asList())
            .field(of("rotation", PhenoType.FLOAT).asList()
                    .doc("Base orientation applied to anything anchored here (ZYX degrees)."))
            .field(of("mirror", PhenoType.BOOL)
                    .doc("Render anchored geometry mirrored across X (one authored ear fits both sides)."))
            .field(of("rig", PhenoType.STRING)
                    .doc("Scope to one rig id; empty = universal. A rig-scoped point overrides a same-id universal one."))
            .field(of("tags", PhenoType.STRING).asList().doc("Tags attachments target (ear, tail_root, ...)."))
            .build();
}
