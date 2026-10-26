package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;

/**
 * Tests {@code condition} on the other side of the context: in a story, the player instead of
 * the villager telling it. False when there is no other.
 * <pre>{ "type": "pheno:other", "condition": { "type": "pheno:state", "state": "townstead_state:dhampir" } }</pre>
 */
public final class OtherConditionType implements ConditionType {
    public static final String KEY = "pheno:other";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Condition inner = Conditions.parse(json.get("condition"));
        if (inner == null) return null;
        return ctx -> ctx.other() != null && inner.test(new ConditionContext(ctx.other(), ctx.entity()));
    }
}
