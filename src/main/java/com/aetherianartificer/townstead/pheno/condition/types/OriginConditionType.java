package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;

/**
 * Tests {@code condition} on the bearer of the power, wherever the focus is now: on the wearer of
 * a ward, on a creature in an area, on a creature a dash hit. False when there is no bearer.
 * <pre>{ "type": "pheno:origin", "condition": { "type": "pheno:resource", "resource": "examplepack:charge", "min": 10 } }</pre>
 */
public final class OriginConditionType implements ConditionType {
    public static final String KEY = "pheno:origin";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Condition inner = Conditions.parse(json.get("condition"));
        if (inner == null) return null;
        return ctx -> ctx.origin() != null && inner.test(new ConditionContext(ctx.origin(), ctx.entity(), ctx.origin()));
    }
}
