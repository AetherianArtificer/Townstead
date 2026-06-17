package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Makes the entity glow (a self outline), optionally only while a {@link Condition}
 * holds. Applied server-side via the forced-glowing flag, which syncs to all
 * viewers; the outline colour follows vanilla team colouring (custom per-gene
 * colour is not yet supported). Maps Apoli's {@code self_glow}.
 *
 * <p>JSON: {@code { "type":"pheno:glow",
 * "condition":{ "type":"pheno:in_water" } }}</p>
 */
public final class GlowGeneType implements GeneType {

    public static final String KEY = "pheno:glow";

    public record Instance(@Nullable Condition condition) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(condition);
    }
}
