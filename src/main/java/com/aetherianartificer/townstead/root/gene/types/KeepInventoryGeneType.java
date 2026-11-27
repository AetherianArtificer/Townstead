package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;

/**
 * A race whose players keep their inventory and XP on death. Enforced by canceling
 * the inventory drop and copying it onto the respawned player.
 *
 * <p>JSON: {@code { "type":"pheno:keep_inventory" }}. An optional {@code condition} is read at the
 * moment of death.</p>
 */
public final class KeepInventoryGeneType implements GeneType {

    @Override
    public com.aetherianartificer.townstead.root.gene.GeneLayer layer() {
        return com.aetherianartificer.townstead.root.gene.GeneLayer.BEING;
    }

    public static final String KEY = "pheno:keep_inventory";

    /** {@code condition} is read when the bearer dies; null keeps the inventory always. */
    public record Instance(@org.jetbrains.annotations.Nullable com.aetherianartificer.townstead.pheno.condition.Condition condition)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        com.aetherianartificer.townstead.pheno.condition.Condition condition = json.has("condition")
                ? com.aetherianartificer.townstead.pheno.condition.Conditions.parse(json.get("condition")) : null;
        if (json.has("condition") && condition == null) return null;
        return new Instance(condition);
    }
}
