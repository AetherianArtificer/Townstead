package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.hunger.FoodSafety;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;

/**
 * True when the entity would eat the item in question (a gift being offered): their diet accepts
 * it, the cannibalism setting lets them eat it, and it does them no harm. False with no item.
 *
 * <p>JSON: {@code { "type":"pheno:would_eat" }}</p>
 */
public final class WouldEatConditionType implements ConditionType {
    public static final String KEY = "pheno:would_eat";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        return ctx -> ctx.entity() != null && !ctx.item().isEmpty()
                && FoodSafety.isSafeNutritiousFood(ctx.item(), ctx.entity());
    }
}
