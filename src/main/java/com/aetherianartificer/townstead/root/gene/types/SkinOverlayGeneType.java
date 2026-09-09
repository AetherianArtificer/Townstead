package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A heritable skin-overlay layer: a player-format (64x64) texture rendered between
 * MCA's skin and face layers, so it paints ON the skin, under the eyes, clothing,
 * and hair — orcish brows and noses, wrinkles, freckles, scars, war paint. The
 * texture is a data-pack texture ({@code data/<ns>/textures/**.png}, synced to
 * clients over the blob pipeline, no resource pack).
 *
 * <p>{@code tint} colours the overlay: {@code "#RRGGBB"}, {@code "skin"} (the
 * bearer's resolved skin tone), {@code "hair"} (their rendered hair colour), or
 * omitted for the texture's own colours. {@code tint_blend} accepts {@code multiply}
 * (default), {@code screen}, {@code overlay}, or {@code color}; {@code tint_strength}
 * fades that result from {@code 0} to {@code 1}. Author skin-matching detail in grayscale
 * plus {@code "skin"}, the same contract attachments use.</p>
 *
 * <p>A multi-variant gene whose options each carry their own {@code texture} is a
 * heritable style swap (three war-paint patterns inheriting Mendelian-style).</p>
 *
 * <p>{@code order} optionally controls stacking. Lower values render first and higher values
 * render later (on top). It defaults to {@code 0}; equal values are ordered by gene id.</p>
 *
 * <p>JSON: {@code { "type":"townstead_roots:skin_overlay",
 * "texture":"my_pack:textures/overlay/orc_face.png", "tint":"skin",
 * "tint_blend":"color", "tint_strength":1.0, "order":10 }}</p>
 */
public final class SkinOverlayGeneType implements GeneType {

    public static final String KEY = "townstead_roots:skin_overlay";

    public record Instance(String texture, String tint, int order, int tintBlend,
                           float tintStrength) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() {
            return GeneDisplay.skinOverlay(texture, tint, order, tintBlend, tintStrength);
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        String texture = GsonHelper.getAsString(json, "texture", "");
        if (texture.isBlank()) return null;
        int blend = switch (GsonHelper.getAsString(json, "tint_blend", "multiply")) {
            case "screen" -> 1;
            case "overlay" -> 2;
            case "color" -> 3;
            default -> 0;
        };
        float strength = Math.max(0f, Math.min(1f,
                GsonHelper.getAsFloat(json, "tint_strength", 1f)));
        return new Instance(texture, GsonHelper.getAsString(json, "tint", ""),
                GsonHelper.getAsInt(json, "order", 0), blend, strength);
    }
}
