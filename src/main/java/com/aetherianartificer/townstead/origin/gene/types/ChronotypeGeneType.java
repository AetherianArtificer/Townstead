package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A race's daily activity rhythm (diurnal, nocturnal, crepuscular). Display-only
 * for now; the {@code chronotype} value is stored to bias Townstead's sleep
 * schedule / chronotype in a later effects phase.
 *
 * <p>JSON: {@code { "type":"townstead_origins:chronotype", "chronotype":"diurnal" }}</p>
 */
public final class ChronotypeGeneType implements GeneType {

    public static final String KEY = "townstead_origins:chronotype";

    public record Instance(String chronotype) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String chronotype = GsonHelper.getAsString(json, "chronotype", "");
        if (chronotype.isBlank()) return null;
        return new Instance(chronotype);
        // TODO(effects): bias the villager's sleep window / chronotype.
    }
}
