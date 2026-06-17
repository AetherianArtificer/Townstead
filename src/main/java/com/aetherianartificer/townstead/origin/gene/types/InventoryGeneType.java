package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * A personal storage container the bearer opens with an Origin Ability key (Origins'
 * {@code inventory}, e.g. the Shulker's pocket). {@code size} is rounded to a chest row
 * (9-54 slots); {@code slot} (1-8) claims a key (0 auto-assigns); {@code drop_on_death}
 * controls whether the contents survive death (default keep). Player-only at runtime
 * (villagers don't open menus); the contents persist in the player's saved data.
 *
 * <p>JSON: {@code { "type":"pheno:inventory", "size":27, "slot":1 }}</p>
 */
public final class InventoryGeneType implements GeneType {

    public static final String KEY = "pheno:inventory";

    public record Instance(int size, int slot, boolean dropOnDeath) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        int rows = Math.max(1, Math.min(6, (GsonHelper.getAsInt(json, "size", 27) + 8) / 9));
        int slot = GsonHelper.getAsInt(json, "slot", 0);
        boolean dropOnDeath = GsonHelper.getAsBoolean(json, "drop_on_death", false);
        return new Instance(rows * 9, slot, dropOnDeath);
    }
}
