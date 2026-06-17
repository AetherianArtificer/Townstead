package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Hostile mobs ignore the bearer (Apugli's {@code mobs_ignore}). An optional
 * {@code mob_condition} (entity condition on the mob) limits which mobs are pacified;
 * an optional {@code bientity_condition} gates on the mob/bearer relationship. Enforced
 * by {@code MobsIgnore} off the change-target event.
 *
 * <p>JSON: {@code { "type":"pheno:mobs_ignore",
 * "mob_condition":{ "type":"pheno:entity_type", "entity_type":"minecraft:zombie" } }}</p>
 */
public final class MobsIgnoreGeneType implements GeneType {

    public static final String KEY = "pheno:mobs_ignore";

    public record Instance(@Nullable Condition mobCondition, @Nullable BiEntityCondition biEntityCondition)
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
        Condition mobCondition = json.has("mob_condition") ? Conditions.parse(json.get("mob_condition")) : null;
        if (json.has("mob_condition") && mobCondition == null) return null;
        BiEntityCondition biEntityCondition = json.has("bientity_condition")
                ? BiEntityConditions.parse(json.get("bientity_condition")) : null;
        if (json.has("bientity_condition") && biEntityCondition == null) return null;
        return new Instance(mobCondition, biEntityCondition);
    }
}
