package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * A race that does not regenerate health naturally from a full hunger bar (it must
 * heal through food items, potions, regeneration effects, or other sources). Mirrors
 * Apoli's no-natural-regen trait. Enforced by canceling the heal that vanilla's food
 * tick performs (see {@code FoodDataNaturalRegenMixin} + the heal listener); other
 * healing is untouched. A future energy-recovery gene covers the needs-system side.
 *
 * <p>JSON: {@code { "type":"pheno:disable_regen" }}</p>
 */
public final class DisableRegenGeneType implements GeneType {

    public static final String KEY = "pheno:disable_regen";

    public record Instance() implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        return new Instance();
    }
}
