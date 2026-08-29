package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;

/** Selects one of two numeric values using an ordinary Pheno block condition. */
public final class IfValueType implements ValueType {
    public static final String KEY = "pheno:if";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        BlockCondition condition = BlockConditions.parse(json.get("condition"));
        Value thenValue = Values.parse(json.get("then"));
        Value elseValue = Values.parse(json.get("else"));
        if (condition == null || thenValue == null || elseValue == null) return null;
        return context -> condition.test(context.level(), context.focusBlock())
                ? thenValue.get(context) : elseValue.get(context);
    }
}
