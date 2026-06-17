package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * The eye colour for a custom-faced rig: a variant gene whose variants are a palette of {@code tint}
 * colours, multiplied over the (greyscale) eye texture — and, for a glowing eye set, the colour of the
 * full-bright glow. Separate axis from the eye SHAPE ({@code eyes}), so shape × colour combine and
 * heterochromia/coloured glow are possible. The tint rides the catalog variant's existing tint field
 * (like {@code skin_tone}); {@code SpeciesFaceLayer} reads the entity's carried variant tint.
 *
 * <p>JSON variant: {@code { "tint":"#66CCFF" }}</p>
 */
public final class EyeColorGeneType implements GeneType {

    public static final String KEY = "townstead_origins:eye_color";

    public record Instance(int tint) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.color(tint, 0, 1.0f); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance(parseHex(GsonHelper.getAsString(json, "tint", ""), 0xFFFFFF));
    }

    /** Parse {@code #RRGGBB} / {@code RRGGBB} / {@code #RGB}; fall back on anything malformed. */
    private static int parseHex(String raw, int fallback) {
        if (raw == null) return fallback;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() == 3) {
            s = "" + s.charAt(0) + s.charAt(0) + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2);
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
