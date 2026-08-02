package com.aetherianartificer.townstead.client.skin;

/**
 * The skin-tint blend math, shared by the skin-layer mixin (recolours the rendered entity) and
 * the Body picker texture (recolours the gradient square) so both stay identical. Callers pack
 * the tint, blend mode, and strength with {@link #pack} and apply it with {@link #blend}.
 *
 * <p>Blend modes:
 * {@code 0} multiply (darken, white = identity), {@code 1} screen (lighten, black = identity),
 * {@code 2} overlay (both, mid-grey = identity), {@code 3} color (keep the base's brightness,
 * take the tint's hue+saturation — the only mode that desaturates, e.g. ashen dark-elf skin),
 * {@code 4} color-value (color, then the tint's own value scales the brightness — a picker-driven
 * tint where the brightness axis must mean something; authored mode-3 tints rely on the base's
 * brightness surviving, so this is a separate mode, not a change to 3).
 * Strength (0–1) lerps the blended result back toward the untinted base.</p>
 */
public final class SkinBlend {

    private SkinBlend() {}

    /** Mode 3 with the tint's value folded in; the render picks it for gene-rolled tints. */
    public static final int MODE_COLOR_VALUE = 4;

    // ---- packing: bits 0-23 tint RGB, 24-26 mode, 27-31 strength (×31) ----

    public static int pack(int tintRgb, int mode, float strength) {
        int s = Math.round(Math.max(0f, Math.min(1f, strength)) * 31f);
        return ((s & 0x1F) << 27) | ((mode & 0x7) << 24) | (tintRgb & 0xFFFFFF);
    }

    public static int packMode(int packed) { return (packed >>> 24) & 0x7; }

    public static int packTint(int packed) { return packed & 0xFFFFFF; }

    public static float packStrength(int packed) { return ((packed >>> 27) & 0x1F) / 31f; }

    /** Apply a packed tint to a 0xRRGGBB base, returning the blended 0xRRGGBB. */
    public static int blend(int baseRgb, int packed) {
        int mode = packMode(packed);
        int tint = packTint(packed);
        int blended = mode == 3 ? colorBlend(baseRgb, tint)
                : mode == MODE_COLOR_VALUE ? colorValueBlend(baseRgb, tint)
                : rgb(baseRgb, tint, mode);
        return lerpRgb(baseRgb, blended, packStrength(packed));
    }

    // ---- blend primitives ----

    /** Blend one 0–255 channel of {@code base} by {@code tint} under a per-channel mode (0/1/2). */
    public static int channel(int base, int tint, int mode) {
        switch (mode) {
            case 1:  return 255 - (255 - base) * (255 - tint) / 255;
            case 2:  return base < 128 ? 2 * base * tint / 255 : 255 - 2 * (255 - base) * (255 - tint) / 255;
            default: return base * tint / 255;
        }
    }

    /** Blend a packed 0xRRGGBB base by a packed 0xRRGGBB tint under a per-channel mode (0/1/2). */
    public static int rgb(int base, int tint, int mode) {
        int r = channel((base >> 16) & 0xFF, (tint >> 16) & 0xFF, mode);
        int g = channel((base >> 8) & 0xFF, (tint >> 8) & 0xFF, mode);
        int b = channel(base & 0xFF, tint & 0xFF, mode);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * "Color" blend: keep the base's luminance, take the tint's hue+saturation. Scales the tint to
     * the base's brightness, so a brown base becomes a same-brightness grey-lavender (ashen), and
     * the melanin light→dark gradient is preserved as lighter/darker shades of the race's palette.
     */
    public static int colorBlend(int baseRgb, int tintRgb) {
        float baseL = luma(baseRgb);
        float tintL = Math.max(1f, luma(tintRgb));
        float k = baseL / tintL;
        int r = clamp255(Math.round(((tintRgb >> 16) & 0xFF) * k));
        int g = clamp255(Math.round(((tintRgb >> 8) & 0xFF) * k));
        int b = clamp255(Math.round((tintRgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * "Color" blend with the tint's own value respected: {@link #colorBlend} pins the result to the
     * BASE's brightness (and is scale-invariant in the tint), which makes a picker's brightness axis
     * a no-op — and a near-black pick either explodes into a vivid colour (hue/sat survive the
     * normalization) or, quantized, collapses to black. Here the tint's value scales the colorized
     * result, so white ≈ mode 3, a dark pick is genuinely dark, and the ramp between is smooth.
     */
    public static int colorValueBlend(int baseRgb, int tintRgb) {
        int colored = colorBlend(baseRgb, tintRgb);
        int v = Math.max((tintRgb >> 16) & 0xFF, Math.max((tintRgb >> 8) & 0xFF, tintRgb & 0xFF));
        int r = ((colored >> 16) & 0xFF) * v / 255;
        int g = ((colored >> 8) & 0xFF) * v / 255;
        int b = (colored & 0xFF) * v / 255;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Shade a {@code hue} by a skin colour's brightness, bounded so the result stays a plausible tone
     * (a dark skin darkens it but never to black; a light one lightens it but never blows out). Used
     * by the rig-tone render and its WYSIWYG picker so both match. Skin luma 0..1 maps to a 0.6..1.15
     * multiplier on the hue.
     */
    public static int shadeByLuma(int hue, int skinRgb) {
        float k = 0.6f + 0.55f * (luma(skinRgb) / 255f);
        int r = clamp255(Math.round(((hue >> 16) & 0xFF) * k));
        int g = clamp255(Math.round(((hue >> 8) & 0xFF) * k));
        int b = clamp255(Math.round((hue & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    private static float luma(int rgb) {
        return 0.299f * ((rgb >> 16) & 0xFF) + 0.587f * ((rgb >> 8) & 0xFF) + 0.114f * (rgb & 0xFF);
    }

    /** Lerp two 0xRRGGBB colours; pass-through at t<=0 (a) and t>=1 (b). */
    public static int lerpRgb(int a, int b, float t) {
        if (t <= 0f) return a;
        if (t >= 1f) return b;
        int rr = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bb = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (rr << 16) | (gg << 8) | bb;
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
