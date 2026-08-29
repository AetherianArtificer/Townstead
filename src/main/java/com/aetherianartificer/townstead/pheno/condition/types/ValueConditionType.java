package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:value} — the general gate: compare one number to another.
 *
 * <pre>
 *   { "type": "pheno:value",
 *     "value":      { "type": "pheno:bond_count", "kind": "townstead:marriage" },
 *     "comparison": "&lt;",
 *     "compare_to": { "type": "pheno:bond_max",   "kind": "townstead:marriage" } }
 * </pre>
 *
 * Either side may be a literal number or any registered value, which is what
 * lets rules live in data instead of in engine flags: the arity of a bond, the
 * cap on a life, a scope's threshold are all just numbers being compared.
 * Keyword names match {@code pheno:count}'s existing {@code comparison} /
 * {@code compare_to}.
 *
 * <p>Subject-capable exactly when both sides are, so a gate that needs live
 * world state stays honest about it.</p>
 */
public final class ValueConditionType implements ConditionType {

    public static final String KEY = "pheno:value";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Value left = Values.parse(json.get("value"));
        Value right = Values.parse(json.get("compare_to"));
        if (left == null || right == null) return null;
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        boolean supportsSubject = left.supportsSubject() && right.supportsSubject();
        return new Condition() {
            @Override
            public boolean test(com.aetherianartificer.townstead.pheno.condition.ConditionContext ctx) {
                SelectorContext frame = SelectorContext.of(ctx);
                return comparison.compare(left.get(frame), right.get(frame));
            }

            @Override
            public boolean supportsSubject() {
                return supportsSubject;
            }
        };
    }
}
