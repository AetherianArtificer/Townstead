package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite conditions: {@code and}/{@code or} over a {@code "conditions"} array,
 * and {@code not} over a single {@code "condition"}. One instance registered per
 * mode. If any child fails to parse the whole composite is rejected (returns
 * {@code null}), so a partially-understood gate never silently loosens.
 */
public final class LogicConditionType implements ConditionType {

    public enum Mode { AND, OR, NOT }

    private final String key;
    private final Mode mode;

    public LogicConditionType(String key, Mode mode) {
        this.key = key;
        this.mode = mode;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Condition parse(JsonObject json) {
        if (mode == Mode.NOT) {
            Condition inner = Conditions.parse(json.get("condition"));
            return inner == null ? null : inner.negate();
        }
        JsonArray array = GsonHelper.getAsJsonArray(json, "conditions", new JsonArray());
        List<Condition> children = new ArrayList<>();
        for (var element : array) {
            Condition child = Conditions.parse(element);
            if (child == null) return null;
            children.add(child);
        }
        if (children.isEmpty()) return Conditions.ALWAYS;
        // A composite can answer about a subject only if every part of it can.
        boolean supportsSubject = children.stream().allMatch(Condition::supportsSubject);
        boolean all = mode == Mode.AND;
        return new Condition() {
            @Override
            public boolean test(com.aetherianartificer.townstead.pheno.condition.ConditionContext ctx) {
                return all ? children.stream().allMatch(c -> c.test(ctx))
                        : children.stream().anyMatch(c -> c.test(ctx));
            }

            @Override
            public boolean supportsSubject() {
                return supportsSubject;
            }
        };
    }
}
