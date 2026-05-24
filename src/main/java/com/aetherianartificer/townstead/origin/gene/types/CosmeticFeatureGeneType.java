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
 * Boolean ("have it or not") gene: a cosmetic feature a race either has or
 * doesn't (e.g. horns, a glow, webbed hands). Display-complete; applying/rendering
 * the feature is a deferred phase.
 *
 * <p>JSON: {@code { "type":"townstead_origins:cosmetic_feature", "feature":"fae_glow",
 * "model":"townstead_origins:part/fae_glow" }}</p>
 */
public final class CosmeticFeatureGeneType implements GeneType {

    public static final String KEY = "townstead_origins:cosmetic_feature";

    public record Instance(String feature, @Nullable ResourceLocation model) implements GeneInstance {
        @Override
        public String typeKey() { return KEY; }

        @Override
        public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String feature = GsonHelper.getAsString(json, "feature", "");
        if (feature.isBlank()) return null;
        ResourceLocation model = json.has("model")
                ? DataPackLang.parseId(GsonHelper.getAsString(json, "model", ""))
                : null;
        return new Instance(feature, model);
        // TODO(effects): render `model` as a custom layer when present. Deferred.
    }
}
