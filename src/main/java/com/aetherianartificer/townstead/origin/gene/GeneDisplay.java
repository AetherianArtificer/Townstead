package com.aetherianartificer.townstead.origin.gene;

/**
 * The generic, type-agnostic descriptor the picker UI (and the catalog wire)
 * need to draw a gene, independent of the Java type:
 * <ul>
 *   <li>{@link Kind#RANGE}: a bar over {@code [min,max]} on a 0–1 track.</li>
 *   <li>{@link Kind#BOOLEAN}: a presence chip.</li>
 *   <li>{@link Kind#INFLUENCE}: a modifier on another gene's occurrence —
 *       {@code targetId} {@code +/-amount}.</li>
 * </ul>
 */
public record GeneDisplay(Kind kind, float min, float max, String targetId, float amount) {

    public enum Kind { RANGE, BOOLEAN, INFLUENCE }

    public static final GeneDisplay PRESENCE = new GeneDisplay(Kind.BOOLEAN, 0f, 1f, "", 0f);

    public static GeneDisplay range(float min, float max) {
        float lo = clamp01(min);
        float hi = clamp01(max);
        return new GeneDisplay(Kind.RANGE, Math.min(lo, hi), Math.max(lo, hi), "", 0f);
    }

    public static GeneDisplay influence(String targetId, float amount) {
        return new GeneDisplay(Kind.INFLUENCE, 0f, 0f, targetId == null ? "" : targetId, amount);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
