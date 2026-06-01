package com.aetherianartificer.townstead.origin.gene;

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
 *       attachment id, e.g. {@code townstead_origins:elf_ears}); a presence chip.</li>
 *   <li>{@link Kind#VARIANTS}: a weighted pick-one gene; drawn as a categorical
 *       menu (race = options + weights, individual = the expressed option). The
 *       variant list rides the catalog separately, not in this descriptor.</li>
 * </ul>
 */
public record GeneDisplay(Kind kind, float min, float max, String targetId, float amount) {

    public enum Kind { RANGE, BOOLEAN, INFLUENCE, COLOR, ATTACHMENT, VARIANTS }

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

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
