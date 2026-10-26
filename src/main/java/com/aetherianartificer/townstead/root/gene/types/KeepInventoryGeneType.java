package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * A race whose players keep their inventory and XP on death. Enforced by canceling
 * the inventory drop and copying it onto the respawned player.
 *
 * <p>JSON: {@code { "type":"pheno:keep_inventory" }}</p>
 */
public final class KeepInventoryGeneType implements GeneType {

    @Override
    public com.aetherianartificer.townstead.root.gene.GeneLayer layer() {
        return com.aetherianartificer.townstead.root.gene.GeneLayer.BEING;
    }

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
