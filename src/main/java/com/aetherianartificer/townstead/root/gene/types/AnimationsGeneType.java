package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** A conditioned animation identity: matching EMF animation first, built-in pose as the fallback. */
public final class AnimationsGeneType implements GeneType {
    public static final String KEY = "townstead_roots:animations";
    @Override public String key() { return KEY; }
    @Override public boolean conditionControlsExpression() { return true; }

    public record Instance(java.util.List<String> providers) implements GeneInstance {
        public Instance { providers = java.util.List.copyOf(providers); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() {
            return new GeneDisplay(GeneDisplay.Kind.ANIMATIONS, 0, 1,
                    String.join(";", providers), 0);
        }
    }

    @Override public GeneInstance parse(JsonObject json) {
        java.util.List<String> providers = new java.util.ArrayList<>();
        if (json.has("providers")) {
            if (!json.get("providers").isJsonArray()) return null;
            for (var value : json.getAsJsonArray("providers")) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
                String provider = value.getAsString();
                if (!provider.equals("humanoid")
                        && com.aetherianartificer.townstead.data.DataPackLang.parseId(provider) == null) return null;
                providers.add(provider);
            }
        } else {
            // Retain the original single-animation spelling without inventing fallback entries.
            String animation = GsonHelper.getAsString(json, "animation", "");
            if (!animation.equals("minecraft:zombie")) return null;
            providers.add(animation);
        }
        return providers.isEmpty() ? null : new Instance(providers);
    }
}
