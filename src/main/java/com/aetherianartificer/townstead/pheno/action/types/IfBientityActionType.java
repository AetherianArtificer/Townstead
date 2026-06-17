package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditions;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * Runs {@code if_action} when a bi-entity {@code condition} holds for the actor/target
 * pair (the action's {@code entity()} and {@code other()}), else the optional
 * {@code else_action}. This is the consumer of bi-entity conditions: pair it with a
 * combat trigger or a {@code target_action} so a relationship (can-see, distance, owner)
 * gates the effect. No-op-to-else when there is no target.
 *
 * <p>JSON: {@code { "type":"pheno:if_bientity",
 * "condition":{ "type":"pheno:can_see" },
 * "if_action":{ "type":"pheno:target_action",
 *               "action":{ "type":"pheno:damage", "amount":4 } } }}</p>
 */
public final class IfBientityActionType implements ActionType {

    public static final String KEY = "pheno:if_bientity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        BiEntityCondition condition = BiEntityConditions.parse(json.get("condition"));
        Action ifAction = Actions.parse(json.get("if_action"));
        if (condition == null || ifAction == null) return null;
        @Nullable Action elseAction = json.has("else_action") ? Actions.parse(json.get("else_action")) : null;
        return ctx -> {
            boolean holds = ctx.other() != null && condition.test(ctx.entity(), ctx.other());
            if (holds) {
                ifAction.run(ctx);
            } else if (elseAction != null) {
                elseAction.run(ctx);
            }
        };
    }
}
