package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * A born cannibal: this individual may eat their own kind whenever the cannibalism setting is at
 * the trait tier or above. One of two ways to be one — the other is acquired through starvation
 * and lives on the villager's Life state — and {@code CannibalismPolicy.isCannibal} is the only
 * place that asks either question.
 *
 * <p>Deliberately a plain presence gene rather than an MCA {@code trait_occurrence}: nothing
 * about it should touch MCA's trait storage or its inheritance quirks.</p>
 *
 * <p>JSON: {@code { "type":"townstead_roots:cannibal" }}</p>
 */
public final class CannibalGeneType implements GeneType {

    public static final String KEY = "townstead_roots:cannibal";

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
