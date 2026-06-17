package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Hides model-part groups ({@code head}/{@code body}/{@code arms}/{@code legs}) by
 * zeroing their render scale. Maps Apoli's {@code prevent_feature_render}. The part
 * list rides the catalog; the villager render mixin reads it client-side.
 *
 * <p>JSON: {@code { "type":"pheno:hide_feature", "features":["arms"] }}</p>
 */
public final class HideFeatureGeneType implements GeneType {

    public static final String KEY = "pheno:hide_feature";

    private static final List<String> GROUPS = List.of("head", "body", "arms", "legs");

    public record Instance(List<String> features) implements GeneInstance {
        public Instance { features = List.copyOf(features); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.hideFeature(features); }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        JsonArray array = GsonHelper.getAsJsonArray(json, "features", new JsonArray());
        List<String> features = new ArrayList<>();
        for (var element : array) {
            String group = element.getAsString().toLowerCase(Locale.ROOT);
            if (GROUPS.contains(group) && !features.contains(group)) features.add(group);
        }
        if (features.isEmpty()) return null;
        return new Instance(features);
    }
}
