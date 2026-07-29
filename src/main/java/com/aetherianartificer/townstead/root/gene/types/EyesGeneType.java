package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * An eye set, on either body path:
 *
 * <ul>
 *   <li><b>Custom rig</b> (skeletownies): the sprite-strip {@code texture} is drawn as an overlay
 *       quad on the rig's face plane by {@code SpeciesFace}. Each frame is the full head front.</li>
 *   <li><b>Humanoid body</b>: the strip replaces MCA's own eyes — each frame is the 8-wide eye band
 *       composited into the head front of a skin-format texture and drawn on MCA's face shell by
 *       {@code HumanoidEyes}, with MCA's {@code FaceLayer} suppressed for that bearer.</li>
 * </ul>
 *
 * <p>Frames run left to right in the fixed order {@code [open, blink, happy, unhappy]}; a shorter
 * strip falls back to {@code open}, and a single-frame humanoid set gets its blink derived from the
 * art (each column's darkest pixel, collapsed to the bottom row). {@code glow} draws the set
 * emissive. On the humanoid path {@code row} is the head-front UV row the strip's top lands on
 * (default: bottom-aligned to row 14, where vanilla eyes sit) and {@code tint} colours it —
 * {@code "#RRGGBB"}, {@code "eye_color"} (the bearer's carried {@code eye_color} variant, for
 * greyscale art that inherits its colour), or omitted for the texture's own colours. A rig set is
 * always tinted by the {@code eye_color} gene.</p>
 *
 * <p>Several variants make the gene a heritable style swap; each option carries its own
 * {@code texture} and {@code glow}, while {@code row}/{@code tint} are shared across the set.</p>
 *
 * <p>JSON: {@code { "type":"townstead_roots:eyes", "texture":"ns:textures/face/eyes/round.png",
 * "glow":true }}</p>
 */
public final class EyesGeneType implements GeneType {

    public static final String KEY = "townstead_roots:eyes";

    /** {@code row} sentinel: place the strip so its bottom row lands on the vanilla eye baseline. */
    public static final int AUTO_ROW = -1;

    // One eye set per creature: every eyes gene shares a locus so cross-ancestry
    // children inherit them as competing alleles instead of stacking both.
    private static final net.minecraft.resources.ResourceLocation LOCUS =
            com.aetherianartificer.townstead.data.DataPackLang.parseId(KEY);

    public record Instance(String texture, boolean glow, int row, String tint) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.eyes(texture, glow, row, tint); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance(GsonHelper.getAsString(json, "texture", ""),
                GsonHelper.getAsBoolean(json, "glow", false),
                GsonHelper.getAsInt(json, "row", AUTO_ROW),
                GsonHelper.getAsString(json, "tint", ""));
    }

    @Override
    public net.minecraft.resources.ResourceLocation defaultLocus(GeneInstance instance) {
        return LOCUS;
    }
}
