package com.aetherianartificer.townstead.origin.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.DimensionsConditionType.Which;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import static com.aetherianartificer.townstead.pheno.condition.types.DimensionsConditionType.parseWhich;

/**
 * Compares the actor's scale against the target's (Apugli's Pehkui {@code compare_scales}, reread from
 * MCA's villager scale factors). {@code which} picks the axis ({@code width}/{@code height}/
 * {@code both}, default both), {@code comparison} the operator: true when the actor's scale
 * {@code comparison} the target's holds for every selected axis.
 *
 * <p>JSON: {@code { "type":"pheno:compare_scales", "which":"both", "comparison":">=" }}</p>
 */
public final class CompareScalesBiConditionType implements BiEntityConditionType {

    public static final String KEY = "pheno:compare_scales";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BiEntityCondition parse(JsonObject json) {
        Which which = parseWhich(GsonHelper.getAsString(json, "which", "both"));
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        return (actor, target) -> {
            if (which != Which.HEIGHT
                    && !comparison.compare(ScaleConditionType.horizontal(actor), ScaleConditionType.horizontal(target))) {
                return false;
            }
            if (which != Which.WIDTH
                    && !comparison.compare(ScaleConditionType.vertical(actor), ScaleConditionType.vertical(target))) {
                return false;
            }
            return true;
        };
    }
}
