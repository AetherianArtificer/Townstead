package com.aetherianartificer.townstead.client.gui.dialogue.effect;

import net.minecraft.util.Mth;

/**
 * Mutable render state for a single character during effect-driven rendering.
 * Effects modify these values; the renderer uses them to draw the character.
 */
public class CharRenderState {
    /** Position offset from the character's base position. */
    public float x, y;
    /** Color channels (0-1). */
    public float r, g, b, a;
    /** Uniform scale factor (1.0 = normal). */
    public float scale;

    public void reset(float baseR, float baseG, float baseB, float baseA) {
        x = 0;
        y = 0;
        r = baseR;
        g = baseG;
        b = baseB;
        a = baseA;
        scale = 1.0f;
    }

    /** Convert float RGBA to packed ARGB int for Minecraft's drawString. */
    public int toArgb() {
        int ia = Mth.clamp((int) (a * 255), 0, 255);
        int ir = Mth.clamp((int) (r * 255), 0, 255);
        int ig = Mth.clamp((int) (g * 255), 0, 255);
        int ib = Mth.clamp((int) (b * 255), 0, 255);
        return (ia << 24) | (ir << 16) | (ig << 8) | ib;
    }
}
