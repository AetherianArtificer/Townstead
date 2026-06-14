package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What liquid a race hydrates on (water, milk, blood, …). Display-only for now;
 * the {@code liquid} value is stored to gate Townstead's thirst system in a later
 * effects phase.
 *
 * <p>JSON: {@code { "type":"townstead_origins:hydration", "liquid":"water" }}</p>
 */
public final class HydrationGeneType implements GeneType {

    public static final String KEY = "townstead_origins:hydration";

    public record Instance(String liquid) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String liquid = GsonHelper.getAsString(json, "liquid", "");
        if (liquid.isBlank()) return null;
        return new Instance(liquid);
        // TODO(effects): gate which liquids quench thirst by this value.
    }
}
