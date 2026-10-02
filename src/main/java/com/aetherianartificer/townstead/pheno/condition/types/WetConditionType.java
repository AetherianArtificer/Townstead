package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.google.gson.JsonObject;

/** True in water/rain and while a Townstead villager is still drying. */
public final class WetConditionType implements ConditionType {
    public static final String KEY = "pheno:wet";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        return ctx -> ctx.entity() != null && TemperatureData.isWet(ctx.entity());
    }
}
