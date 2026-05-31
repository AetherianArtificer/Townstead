package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * A race's skin colour as a single {@code tint} (HTML hex) blended over MCA's exact
 * melanin×hemoglobin villager skin, so the full vanilla skin gradient is preserved and merely
 * shifted toward the race's palette. {@code blend} picks how:
 * <ul>
 *   <li>{@code multiply} (default) — darkens / hue-shifts; <b>white</b> is the identity, so
 *       Overworlder ({@code tint:#FFFFFF}) renders pixel-exact vanilla and every origin runs
 *       this same path.</li>
 *   <li>{@code screen} — lightens / hue-shifts; <b>black</b> is the identity.</li>
 *   <li>{@code overlay} — darkens below mid-grey, lightens above it; <b>#808080</b> is the
 *       identity (one tint covers both directions).</li>
 *   <li>{@code color} — keeps the base's brightness but takes the tint's hue+saturation; the
 *       only mode that desaturates (e.g. ashen dark-elf skin over a brown base).</li>
 * </ul>
 * {@code strength} (0–1, default 1) scales how strongly the tint applies. Legacy {@code from}/
 * {@code to} genes are read by their {@code from}.
 *
 * <p>JSON: {@code { "type":"townstead_origins:skin_tone", "tint":"#8A8FA0", "blend":"multiply",
 * "strength":1.0 }}</p>
 */
public final class SkinToneGeneType implements GeneType {

    public static final String KEY = "townstead_origins:skin_tone";

    /** Blend ordinals (shared with the skin-layer mixin): 0 multiply, 1 screen, 2 overlay. */
    public record Instance(int tint, int blend, float strength) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.color(tint, blend, strength); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String raw = json.has("tint")
                ? GsonHelper.getAsString(json, "tint", "")
                : GsonHelper.getAsString(json, "from", "");   // legacy gradient gene: tint by its 'from'
        int tint = parseHex(raw, 0xFFFFFF);                   // default white = no tint (exact vanilla)
        int blend = parseBlend(GsonHelper.getAsString(json, "blend", "multiply"));
        float strength = GsonHelper.getAsFloat(json, "strength", 1.0f);   // how strongly the tint applies (0–1)
        return new Instance(tint, blend, strength);
    }

    private static int parseBlend(String s) {
        switch (s.toLowerCase(Locale.ROOT)) {
            case "screen":  return 1;
            case "overlay": return 2;
            case "color":   return 3;
            default:        return 0; // multiply
        }
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
