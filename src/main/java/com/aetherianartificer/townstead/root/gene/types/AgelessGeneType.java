package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * Freezes the carrier's aging while expressed, without making it deathless (that is Immortal).
 * Unlike an intrinsic ageless life cycle it can be granted and withdrawn, so a state such as
 * vampirism can stop a villager aging for as long as it lasts.
 *
 * <p>JSON: {@code { "type":"townstead_roots:ageless" }}</p>
 */
public final class AgelessGeneType implements GeneType {

    public static final String KEY = "townstead_roots:ageless";

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
