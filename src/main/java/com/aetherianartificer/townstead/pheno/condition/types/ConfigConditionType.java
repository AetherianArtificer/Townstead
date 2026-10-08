package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.data.ConfigGate;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;

/** Reads an ordinary TOML value without coupling a condition to the owning mod. */
public final class ConfigConditionType implements ConditionType {
    public static final String KEY = "pheno:config";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        if (!ConfigGate.valid(json)) return null;
        return ctx -> Boolean.TRUE.equals(ConfigGate.evaluate(json, ctx.level()));
    }
}
