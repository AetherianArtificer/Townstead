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
 * omitted for the texture's own colours. Author skin-matching detail in grayscale
 * plus {@code "skin"}, the same contract attachments use.</p>
 *
 * <p>A multi-variant gene whose options each carry their own {@code texture} is a
 * heritable style swap (three war-paint patterns inheriting Mendelian-style).</p>
 *
 * <p>{@code order} optionally controls stacking. Lower values render first and higher values
 * render later (on top). It defaults to {@code 0}; equal values are ordered by gene id.</p>
 *
 * <p>JSON: {@code { "type":"townstead_roots:skin_overlay",
 * "texture":"my_pack:textures/overlay/orc_face.png", "tint":"skin", "order":10 }}</p>
 */
public final class SkinOverlayGeneType implements GeneType {

    public static final String KEY = "townstead_roots:skin_overlay";

    public record Instance(String texture, String tint, int order) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() {
            return GeneDisplay.skinOverlay(texture, tint, order);
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
        return new Instance(texture, GsonHelper.getAsString(json, "tint", ""),
                GsonHelper.getAsInt(json, "order", 0));
    }
}
