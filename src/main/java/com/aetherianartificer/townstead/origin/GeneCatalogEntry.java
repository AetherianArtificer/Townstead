package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;

import java.util.List;

/**
 * One gene's display data for the catalog sync / picker, flattened to primitives
 * so the client renders it without the server-only {@code GeneRegistry} or Java
 * gene types.
 *
 * <p>{@code displayKind} is {@link GeneDisplay.Kind#ordinal()}
 * (0 = RANGE, 1 = BOOLEAN, 2 = INFLUENCE, 3 = COLOR). {@code min}/{@code max} apply to
 * RANGE; {@code targetId} to INFLUENCE (target gene) and COLOR (two hex endpoints
 * {@code "rrggbb-rrggbb"}, read via {@link #colorFrom()}/{@link #colorTo()});
 * {@code amount} to INFLUENCE. {@code dominanceOrdinal} is
 * {@link com.aetherianartificer.townstead.origin.gene.Dominance#ordinal()}
 * (0 = DOMINANT, 1 = RECESSIVE); {@code locus} is empty when none.</p>
 */
public record GeneCatalogEntry(
        String id,
        String name,
        String description,
        String category,
        int displayKind,
        float min,
        float max,
        String targetId,
        float amount,
        int dominanceOrdinal,
        String locus,
        int weight,
        List<Variant> variants
) {
    public GeneCatalogEntry {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    /** One option of a VARIANTS gene: its id, resolved label, and roll weight. */
    public record Variant(String id, String label, int weight) {}

    public boolean isVariants() {
        return displayKind == GeneDisplay.Kind.VARIANTS.ordinal();
    }

    public boolean isRange() {
        return displayKind == GeneDisplay.Kind.RANGE.ordinal();
    }

    public boolean isInfluence() {
        return displayKind == GeneDisplay.Kind.INFLUENCE.ordinal();
    }

    public boolean isColor() {
        return displayKind == GeneDisplay.Kind.COLOR.ordinal();
    }

    public boolean isAttachment() {
        return displayKind == GeneDisplay.Kind.ATTACHMENT.ordinal();
    }

    /** For ATTACHMENT genes, the attachment id (rides in {@code targetId}). */
    public String attachmentId() { return targetId; }

    /**
     * COLOR tint (RGB), parsed from {@code targetId} {@code "rrggbb-rrggbb"} (both parts equal —
     * the gene carries one tint colour, multiplied over MCA's exact skin; white = unchanged).
     */
    public int colorFrom() { return colorPart(0, 0xFFFFFF); }
    public int colorTo()   { return colorPart(1, colorFrom()); }

    /** COLOR blend mode (rides in {@code amount}): 0 multiply/darken, 1 screen/lighten, 2 overlay/both. */
    public int blendMode() { return Math.round(amount); }

    /** COLOR blend strength 0–1 (rides in {@code min}): how strongly the tint is applied. */
    public float blendStrength() { return Math.max(0f, Math.min(1f, min)); }

    private int colorPart(int idx, int fallback) {
        if (targetId == null) return fallback;
        String[] parts = targetId.split("-");
        if (idx >= parts.length) return fallback;
        try {
            return Integer.parseInt(parts[idx].trim(), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean isRecessive() {
        return dominanceOrdinal == com.aetherianartificer.townstead.origin.gene.Dominance.RECESSIVE.ordinal();
    }
}
