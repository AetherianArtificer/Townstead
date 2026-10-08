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
        if (json.has("entity_condition")) {
            if (json.has("condition")) return null;
            var condition = com.aetherianartificer.townstead.pheno.condition.Conditions.parse(json.get("entity_condition"));
            Value thenValue = Values.parse(json.get("then")), elseValue = Values.parse(json.get("else"));
            if (condition == null || thenValue == null || elseValue == null) return null;
            Value compiled = context -> condition.test(com.aetherianartificer.townstead.pheno.condition.ConditionContext.of(context))
                    ? thenValue.get(context) : elseValue.get(context);
            return condition.supportsSubject() && thenValue.supportsSubject() && elseValue.supportsSubject()
                    ? Value.subjectAware(compiled) : compiled;
        }
        BlockCondition condition = BlockConditions.parse(json.get("condition"));
        Value thenValue = Values.parse(json.get("then"));
        Value elseValue = Values.parse(json.get("else"));
        if (condition == null || thenValue == null || elseValue == null) return null;
        return context -> condition.test(context.level(), context.focusBlock())
                ? thenValue.get(context) : elseValue.get(context);
    }
}
