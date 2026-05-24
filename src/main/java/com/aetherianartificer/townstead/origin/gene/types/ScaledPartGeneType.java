package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Range gene: scales a named body part by a value rolled within {@code [min,max]}
 * (e.g. ear size, tusk length). Display-complete; the actual scaling/rendering
 * is a deferred phase (needs per-villager values + custom render layers).
 *
 * <p>JSON: {@code { "type":"townstead_origins:scaled_part", "part":"ears",
 * "min":0.6, "max":1.0, "model":"townstead_origins:part/elf_ears" }}</p>
 */
public final class ScaledPartGeneType implements GeneType {

    public static final String KEY = "townstead_origins:scaled_part";

    public record Instance(String part, float min, float max, @Nullable ResourceLocation model)
            implements GeneInstance {
        @Override
        public String typeKey() { return KEY; }

        @Override
        public GeneDisplay display() { return GeneDisplay.range(min, max); }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String part = GsonHelper.getAsString(json, "part", "");
        if (part.isBlank()) return null;
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", 1f);
        ResourceLocation model = json.has("model")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "model", ""))
                : null;
        return new Instance(part, min, max, model);
        // TODO(effects): roll a per-villager value in [min,max] and scale `part`;
        // render `model` as a custom layer. Deferred to the gene-effects phase.
    }
}
