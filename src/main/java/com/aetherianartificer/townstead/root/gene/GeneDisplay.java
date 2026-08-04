package com.aetherianartificer.townstead.root.gene;

/**
 * The generic, type-agnostic descriptor the picker UI (and the catalog wire)
 * need to draw a gene, independent of the Java type:
 * <ul>
 *   <li>{@link Kind#RANGE}: a bar over {@code [min,max]} on a 0–1 track.</li>
 *   <li>{@link Kind#BOOLEAN}: a presence chip.</li>
 *   <li>{@link Kind#INFLUENCE}: a modifier on another gene's occurrence —
 *       {@code targetId} {@code +/-amount}.</li>
 *   <li>{@link Kind#COLOR}: a skin-tone gradient between two RGB endpoints
 *       ({@code targetId} carries {@code "rrggbb-rrggbb"}); drawn as a gradient swatch.</li>
 *   <li>{@link Kind#ATTACHMENT}: a cosmetic model part ({@code targetId} carries the
 *       attachment id, e.g. {@code townstead_roots:elf_ears}); a presence chip.</li>
 *   <li>{@link Kind#VARIANTS}: a weighted pick-one gene; drawn as a categorical
 *       menu (race = options + weights, individual = the expressed option). The
 *       variant list rides the catalog separately, not in this descriptor.</li>
 * </ul>
 */
public record GeneDisplay(Kind kind, float min, float max, String targetId, float amount) {

    public enum Kind { RANGE, BOOLEAN, INFLUENCE, COLOR, ATTACHMENT, VARIANTS, PROPORTIONS, HIDE_FEATURE, ABILITY, OVERLAY, PARTICLE, SUPPRESS_NEED, STUCK_IMMUNITY, BUOYANCY, SKIN_OVERLAY, OPACITY, INNATE_TOOL, BLOCK_BREAK_SPEED }

    public static final GeneDisplay PRESENCE = new GeneDisplay(Kind.BOOLEAN, 0f, 1f, "", 0f);

    public static final GeneDisplay VARIANTS = new GeneDisplay(Kind.VARIANTS, 0f, 1f, "", 0f);

    public static GeneDisplay range(float min, float max) {
        float lo = clamp01(min);
        float hi = clamp01(max);
        return new GeneDisplay(Kind.RANGE, Math.min(lo, hi), Math.max(lo, hi), "", 0f);
    }

    /**
     * A skin-tone tint colour blended over MCA's exact melanin×hemoglobin skin so the full vanilla
     * gradient is preserved and shifted toward the race's palette. {@code targetId} packs the tint
     * as {@code "rrggbb-rrggbb"} (both equal — the swatch is a flat tint); {@code amount} carries
     * the blend ordinal (0 multiply, 1 screen, 2 overlay); {@code min} carries the blend strength
     * (0–1, how strongly the tint is applied).
     */
    public static GeneDisplay color(int tint, int blend, float strength) {
        String packed = String.format(java.util.Locale.ROOT, "%06x-%06x", tint & 0xFFFFFF, tint & 0xFFFFFF);
        return new GeneDisplay(Kind.COLOR, Math.max(0f, Math.min(1f, strength)), 1f, packed, blend);
    }

    public static GeneDisplay influence(String targetId, float amount) {
        return new GeneDisplay(Kind.INFLUENCE, 0f, 0f, targetId == null ? "" : targetId, amount);
    }

    /** A cosmetic attachment (model part); the attachment id rides in {@code targetId}. */
    public static GeneDisplay attachment(String attachmentId) {
        return new GeneDisplay(Kind.ATTACHMENT, 0f, 1f, attachmentId == null ? "" : attachmentId, 0f);
    }

    /**
     * A sized attachment: the heritable size roll's range rides in {@code min}/{@code max}
     * (brackets 1.0, the authored geometry's neutral scale) and {@code amount == 1} flags it,
     * so the character editor can offer a slider over the range.
     */
    public static GeneDisplay sizedAttachment(String attachmentId, float min, float max) {
        return new GeneDisplay(Kind.ATTACHMENT, Math.min(min, max), Math.max(min, max),
                attachmentId == null ? "" : attachmentId, 1f);
    }

    /**
     * Per-part render multipliers for a stocky build (e.g. dwarves): each listed part has the body
     * squash neutralized and is then scaled by its per-axis factors (1.0 = proportioned, no resize).
     * The part→factors map packs into {@code targetId} as {@code "head=1.1,1.1,1.1;body=1.15,1.0,1.4"};
     * a 6-element entry packs as {@code "body=lx,ly,lz:sx,sy,sz"} (lean:stout, lerped by the bearer's
     * rolled width gene), and {@code "@width=min,max"} carries the gene's width range so the lerp
     * spans the race's actual roll. The head/limb render mixin and catalog unpack it. The gene's
     * body-metric ranges (size/width) ride the server side (MCA floats), not this descriptor.
     */
    public static GeneDisplay proportions(java.util.Map<String, float[]> partScales,
                                          com.aetherianartificer.townstead.root.GeneRange widthRange) {
        StringBuilder packed = new StringBuilder();
        boolean lerped = false;
        if (partScales != null) {
            for (java.util.Map.Entry<String, float[]> e : partScales.entrySet()) {
                if (packed.length() > 0) packed.append(';');
                float[] f = e.getValue();
                packed.append(e.getKey()).append('=')
                        .append(String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f", f[0], f[1], f[2]));
                if (f.length >= 6) {
                    packed.append(String.format(java.util.Locale.ROOT, ":%.4f,%.4f,%.4f", f[3], f[4], f[5]));
                    lerped = true;
                }
            }
        }
        if (lerped && widthRange != null) {
            if (packed.length() > 0) packed.append(';');
            packed.append(String.format(java.util.Locale.ROOT, "@width=%.4f,%.4f",
                    widthRange.min(), widthRange.max()));
        }
        return new GeneDisplay(Kind.PROPORTIONS, 0f, 1f, packed.toString(), 0f);
    }

    /**
     * Model-part groups this gene hides ({@code head}/{@code body}/{@code arms}/{@code legs}),
     * packed into {@code targetId} as {@code "head;arms"}. The render mixin zeroes those parts'
     * scale; a presence chip in the picker.
     */
    public static GeneDisplay hideFeature(java.util.List<String> parts) {
        String packed = parts == null ? "" : String.join(";", parts);
        return new GeneDisplay(Kind.HIDE_FEATURE, 0f, 1f, packed, 0f);
    }

    /**
     * An innate ability; {@code targetId} carries the ability key (e.g. {@code night_vision}),
     * {@code amount} the mode ordinal (0 passive, 1 toggle), {@code min} the key slot. Synced so
     * the client can resolve a player's movement abilities for local physics prediction; a presence
     * chip in the picker.
     */
    public static GeneDisplay ability(String abilityKey, int modeOrdinal, int slot) {
        return new GeneDisplay(Kind.ABILITY, slot, 1f, abilityKey == null ? "" : abilityKey, modeOrdinal);
    }

    /**
     * A full-screen HUD overlay texture (e.g. a racial vignette). {@code targetId} carries
     * the texture id; {@code min} the draw alpha (0–1). Synced so the client can blit it for
     * a player whose origin expresses it; a presence chip in the picker.
     */
    public static GeneDisplay overlay(String texture, float alpha) {
        return new GeneDisplay(Kind.OVERLAY, Math.max(0f, Math.min(1f, alpha)), 1f, texture == null ? "" : texture, 0f);
    }

    /**
     * An ambient particle emitter; the emitter parameters pack into {@code targetId} as
     * {@code "particleId;count;spread;speed;yOffset"} so the picker preview can render a
     * matching screen-space approximation (the in-world emitter stays server-side). A
     * presence chip in the list.
     */
    public static GeneDisplay particle(net.minecraft.resources.ResourceLocation particle, int count,
                                       float spread, float speed, float yOffset) {
        String packed = (particle == null ? "" : particle.toString()) + ";" + count + ";"
                + fmt(spread) + ";" + fmt(speed) + ";" + fmt(yOffset);
        return new GeneDisplay(Kind.PARTICLE, 0f, 1f, packed, 0f);
    }

    /**
     * A skin-overlay layer (a player-format texture drawn between MCA's skin and face
     * layers: orcish features, freckles, scars, war paint). Packs into {@code targetId}
     * as {@code "texture;tint;order"} where {@code tint} is {@code ""} (untinted), a hex
     * colour, {@code skin}, or {@code hair}. A presence chip in the list.
     */
    public static GeneDisplay skinOverlay(String texture, String tint) {
        return skinOverlay(texture, tint, 0);
    }

    public static GeneDisplay skinOverlay(String texture, String tint, int order) {
        return new GeneDisplay(Kind.SKIN_OVERLAY, 0f, 1f,
                (texture == null ? "" : texture) + ";" + (tint == null ? "" : tint)
                        + ";" + order, 0f);
    }

    /**
     * An eye set. Packs into {@code targetId} as {@code "texture;glow;row;tint"} so the render
     * side resolves the strip, its emissive flag, the head-front UV row it lands on ({@code -1}
     * = bottom-aligned to the vanilla eye baseline), and its tint spec ({@code ""} own colours,
     * {@code eye_color}, or a hex colour) without a variant. A multi-variant set carries each
     * option's own texture/glow on the variant instead; row and tint stay shared. A presence
     * chip in the picker.
     */
    public static GeneDisplay eyes(String texture, boolean glow, int row, String tint) {
        return new GeneDisplay(Kind.BOOLEAN, 0f, 1f,
                (texture == null ? "" : texture) + ";" + (glow ? 1 : 0) + ";" + row
                        + ";" + (tint == null ? "" : tint), 0f);
    }

    /**
     * The needs this gene switches off ({@code hunger}/{@code thirst}/{@code sleep}), packed into
     * {@code targetId} as {@code "hunger;thirst"} so the interact-screen status bar can hide each
     * suppressed need's icon. The server enforcement reads the gene directly; a presence chip in the picker.
     */
    public static GeneDisplay suppressNeed(java.util.List<String> needs) {
        String packed = needs == null ? "" : String.join(";", needs);
        return new GeneDisplay(Kind.SUPPRESS_NEED, 0f, 1f, packed, 0f);
    }

    /**
     * The blocks this gene moves freely through (cobweb, sweet berry bush), packed into {@code targetId}
     * as {@code "minecraft:cobweb;..."} so the controlling client can resolve the immunity for its own
     * physics prediction (a spider-folk player walking through cobwebs). A presence chip in the picker.
     */
    public static GeneDisplay stuckImmunity(java.util.Set<net.minecraft.resources.ResourceLocation> blocks) {
        StringBuilder packed = new StringBuilder();
        if (blocks != null) {
            for (net.minecraft.resources.ResourceLocation id : blocks) {
                if (packed.length() > 0) packed.append(';');
                packed.append(id.toString());
            }
        }
        return new GeneDisplay(Kind.STUCK_IMMUNITY, 0f, 1f, packed.toString(), 0f);
    }

    /**
     * The fluids this gene nullifies (treats as not-there, so the bearer walks the bottom), packed
     * into {@code targetId} as {@code "minecraft:water;minecraft:lava"} so the controlling client can
     * resolve its own land-movement underwater for local physics prediction (a skeleton-folk player
     * walking the riverbed). A presence chip in the picker.
     */
    public static GeneDisplay buoyancy(java.util.Set<net.minecraft.resources.ResourceLocation> fluids) {
        StringBuilder packed = new StringBuilder();
        if (fluids != null) {
            for (net.minecraft.resources.ResourceLocation id : fluids) {
                if (packed.length() > 0) packed.append(';');
                packed.append(id.toString());
            }
        }
        return new GeneDisplay(Kind.BUOYANCY, 0f, 1f, packed.toString(), 0f);
    }

    /**
     * Conditional body render opacity: {@code min} carries the alpha (1 solid, 0 unseen);
     * the gene's condition rides the catalog's {@code conditionJson}. Synced so the client
     * fade can hold a translucent floor (imperfect invisibility, a standing ghost); a
     * presence chip in the picker.
     */
    public static GeneDisplay opacity(float alpha) {
        return new GeneDisplay(Kind.OPACITY, clamp01(alpha), 1f, "", 0f);
    }

    /**
     * An empty-hand-acts-as-tool gene; {@code targetId} carries the item id (e.g.
     * {@code minecraft:stone_pickaxe}). Synced so the controlling client predicts the same
     * dig speed / harvest result locally (block breaking is client-paced); the gene's
     * condition rides the catalog's {@code conditionJson}. A presence chip in the picker.
     */
    public static GeneDisplay innateTool(String itemId) {
        return new GeneDisplay(Kind.INNATE_TOOL, 0f, 1f, itemId == null ? "" : itemId, 0f);
    }

    /**
     * A block-scoped dig-speed multiplier; {@code targetId} carries the block filter
     * ({@code "#ns:tag"} or a block id), {@code amount} the multiplier. Synced for the same
     * client-paced prediction as {@link #innateTool}; the gene's condition rides the
     * catalog's {@code conditionJson}. A presence chip in the picker.
     */
    public static GeneDisplay blockBreakSpeed(String filterSpec, float value) {
        return new GeneDisplay(Kind.BLOCK_BREAK_SPEED, 0f, 1f, filterSpec == null ? "" : filterSpec, value);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
