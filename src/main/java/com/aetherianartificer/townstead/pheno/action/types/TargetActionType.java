package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;

/**
 * Runs the wrapped action on the <b>target</b> ({@code ActionContext.other()}) with the
 * actor/target roles swapped, so the inner action's {@code entity()} is the target and
 * its {@code other()} is the actor (Apoli's bi-entity {@code target_action}). No-op when
 * there is no counterpart. This is how a single-entity action reaches the other party:
 * {@code target_action(damage)} hurts the target, {@code target_action(add_velocity)}
 * knocks it back.
 *
 * <p>JSON: {@code { "type":"pheno:target_action",
 * "action":{ "type":"pheno:damage", "amount":4 } }}</p>
 */
public final class TargetActionType implements ActionType {

    public static final String KEY = "pheno:target_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            if (ctx.other() != null) inner.run(new ActionContext(ctx.other(), ctx.entity()));
        };
    }
}
