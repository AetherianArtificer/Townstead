package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.function.ToDoubleFunction;

/**
 * A {@code min}/{@code max} range condition over a numeric property of the entity (air,
 * fall distance, food level, XP, …). One instance per property with its own key and
 * value function, so the simple numeric conditions don't each need their own class. A
 * value of {@code NaN} (e.g. a player-only property on a villager) never falls in range.
 */
public final class NumericConditionType implements ConditionType {

    private final String key;
    private final ToDoubleFunction<ConditionContext> value;

    public NumericConditionType(String key, ToDoubleFunction<ConditionContext> value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Condition parse(JsonObject json) {
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        return ctx -> {
            double v = value.applyAsDouble(ctx);
            return v >= min && v <= max;
        };
    }
}
