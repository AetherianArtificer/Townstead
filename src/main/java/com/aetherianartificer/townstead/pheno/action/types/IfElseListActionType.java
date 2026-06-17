package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs the action of the first {@code actions} entry whose condition holds, else the
 * optional {@code fallback_action} (Apoli's meta {@code if_else_list}). Each entry is
 * {@code { "condition":{...}, "action":{...} }}.
 *
 * <p>JSON: {@code { "type":"pheno:if_else_list", "actions":[
 * { "condition":{...}, "action":{...} } ], "fallback_action":{...} }}</p>
 */
public final class IfElseListActionType implements ActionType {

    public static final String KEY = "pheno:if_else_list";

    private record Branch(Condition condition, Action action) {}

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        List<Branch> branches = new ArrayList<>();
        if (json.has("actions") && json.get("actions").isJsonArray()) {
            for (var element : json.getAsJsonArray("actions")) {
                if (!element.isJsonObject()) continue;
                JsonObject entry = element.getAsJsonObject();
                Condition condition = Conditions.parse(entry.get("condition"));
                Action action = Actions.parse(entry.get("action"));
                if (condition != null && action != null) branches.add(new Branch(condition, action));
            }
        }
        Action fallback = json.has("fallback_action") ? Actions.parse(json.get("fallback_action")) : null;
        if (branches.isEmpty() && fallback == null) return null;
        Action fallbackAction = fallback;
        return ctx -> {
            ConditionContext cctx = new ConditionContext(ctx.entity());
            for (Branch branch : branches) {
                if (branch.condition().test(cctx)) {
                    branch.action().run(ctx);
                    return;
                }
            }
            if (fallbackAction != null) fallbackAction.run(ctx);
        };
    }
}
