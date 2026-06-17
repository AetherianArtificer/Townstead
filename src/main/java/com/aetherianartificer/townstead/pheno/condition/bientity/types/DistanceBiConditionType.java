package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the distance between actor and target is within {@code [min, max]} blocks
 * (Apoli's bi-entity {@code distance}).
 *
 * <p>JSON: {@code { "type":"pheno:distance", "max":3.0 }}</p>
 */
public final class DistanceBiConditionType implements BiEntityConditionType {

    public static final String KEY = "pheno:distance";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        double min = GsonHelper.getAsDouble(json, "min", 0);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        return (actor, target) -> {
            double d = actor.distanceTo(target);
            return d >= min && d <= max;
        };
    }
}
