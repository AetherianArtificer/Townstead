package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * A race whose players keep their inventory and XP on death. Enforced by canceling
 * the inventory drop and copying it onto the respawned player.
 *
 * <p>JSON: {@code { "type":"pheno:keep_inventory" }}</p>
 */
public final class KeepInventoryGeneType implements GeneType {

    public static final String KEY = "pheno:keep_inventory";

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
