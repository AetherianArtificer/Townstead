package com.aetherianartificer.townstead.client.skin;

/**
 * The skin-tint blend math, shared by the skin-layer mixin (recolours the rendered entity) and
 * the Body picker texture (recolours the gradient square) so both stay identical. Callers pack
 * the tint, blend mode, and strength with {@link #pack} and apply it with {@link #blend}.
 *
 * <p>Blend modes:
 * {@code 0} multiply (darken, white = identity), {@code 1} screen (lighten, black = identity),
 * {@code 2} overlay (both, mid-grey = identity), {@code 3} color (keep the base's brightness,
 * take the tint's hue+saturation — the only mode that desaturates, e.g. ashen dark-elf skin).
 * Strength (0–1) lerps the blended result back toward the untinted base.</p>
 */
public final class SkinBlend {

    private SkinBlend() {}

    // ---- packing: bits 0-23 tint RGB, 24-25 mode, 26-31 strength (×63) ----

    public static int pack(int tintRgb, int mode, float strength) {
        int s = Math.round(Math.max(0f, Math.min(1f, strength)) * 63f);
        return ((s & 0x3F) << 26) | ((mode & 0x3) << 24) | (tintRgb & 0xFFFFFF);
    }

    public static int packMode(int packed) { return (packed >>> 24) & 0x3; }

    public static int packTint(int packed) { return packed & 0xFFFFFF; }

    public static float packStrength(int packed) { return ((packed >>> 26) & 0x3F) / 63f; }

    /** Apply a packed tint to a 0xRRGGBB base, returning the blended 0xRRGGBB. */
    public static int blend(int baseRgb, int packed) {
        int mode = packMode(packed);
        int tint = packTint(packed);
        int blended = mode == 3 ? colorBlend(baseRgb, tint) : rgb(baseRgb, tint, mode);
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
