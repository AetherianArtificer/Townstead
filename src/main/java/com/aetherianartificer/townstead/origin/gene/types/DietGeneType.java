package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * What a race eats (omnivore, herbivore, carnivore, …). Display-only for now;
 * the {@code diet} value is stored to gate Townstead's hunger nourishment in a
 * later effects phase.
 *
 * <p>JSON: {@code { "type":"townstead_origins:diet", "diet":"omnivore" }}</p>
 */
public final class DietGeneType implements GeneType {

    public static final String KEY = "townstead_origins:diet";

    public record Instance(String diet) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        String diet = GsonHelper.getAsString(json, "diet", "");
        if (diet.isBlank()) return null;
        return new Instance(diet);
        // TODO(effects): gate which foods nourish the villager by this diet.
    }
}
