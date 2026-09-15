package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.google.gson.JsonObject;

/** True while the entity stands in water or under rain: Townstead's own wetness, shared with the temperature need. */
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
