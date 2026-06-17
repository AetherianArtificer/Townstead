package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Runs {@code if_action} when {@code condition} holds on the actor, otherwise the
 * optional {@code else_action} (Apoli's meta {@code if_else}).
 *
 * <p>JSON: {@code { "type":"pheno:if_else",
 * "condition":{ "type":"pheno:submerged" },
 * "if_action":{...}, "else_action":{...} }}</p>
 */
public final class IfElseActionType implements ActionType {

    public static final String KEY = "pheno:if_else";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Condition condition = Conditions.parse(json.get("condition"));
        Action ifAction = Actions.parse(json.get("if_action"));
        if (condition == null || ifAction == null) return null;
        @Nullable Action elseAction = json.has("else_action") ? Actions.parse(json.get("else_action")) : null;
        return ctx -> {
            if (condition.test(new ConditionContext(ctx.entity()))) {
                ifAction.run(ctx);
            } else if (elseAction != null) {
                elseAction.run(ctx);
            }
        };
    }
}
