package com.aetherianartificer.townstead.pheno.condition.bientity.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.DimensionsConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Compares the actor's live bounding-box size against the target's (Apugli's bi-entity
 * {@code compare_dimensions}). {@code which} picks the axis ({@code width}/{@code height}/{@code both},
 * default both), {@code comparison} the operator: true when the actor's size {@code comparison} the
 * target's holds for every selected axis (e.g. {@code ">="} = the actor is at least as large).
 *
 * <p>JSON: {@code { "type":"pheno:compare_dimensions", "which":"width", "comparison":">=" }}</p>
 */
public final class CompareDimensionsBiConditionType implements BiEntityConditionType {

    public static final String KEY = "pheno:compare_dimensions";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        DimensionsConditionType.Which which =
                DimensionsConditionType.parseWhich(GsonHelper.getAsString(json, "which", "both"));
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        return (actor, target) -> {
            if (which != DimensionsConditionType.Which.HEIGHT
                    && !comparison.compare(actor.getBbWidth(), target.getBbWidth())) return false;
            if (which != DimensionsConditionType.Which.WIDTH
                    && !comparison.compare(actor.getBbHeight(), target.getBbHeight())) return false;
            return true;
        };
    }
}
