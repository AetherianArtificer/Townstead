package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A race's skin colour as a range within a named palette/colorway (peers, no
 * privileged default): {@code warm} = the pale-to-deep spectrum (drives MCA
 * melanin), {@code verdant}, {@code ashen}, … = custom tints for other tones.
 * {@code min}/{@code max} narrow the band. Display-only for now (a range chip);
 * the actual tinting — MCA melanin for {@code warm}, a custom skin recolor for
 * others — is a deferred effects/render phase.
 *
 * <p>JSON: {@code { "type":"townstead_origins:skin_tone", "palette":"warm",
 * "min":0.0, "max":1.0 }}</p>
 */
public final class SkinToneGeneType implements GeneType {

    public static final String KEY = "townstead_origins:skin_tone";

    public record Instance(String palette, float min, float max) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.range(min, max); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String palette = GsonHelper.getAsString(json, "palette", "warm");
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", 1f);
        return new Instance(palette, min, max);
        // TODO(effects): "warm" palette -> roll within MCA melanin/hemoglobin;
        // other palettes -> tint the skin texture (custom render).
    }
}
