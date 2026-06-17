package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;

/**
 * Runs the wrapped action on the <b>actor</b> ({@code ActionContext.entity()}). A
 * pass-through that exists for symmetry with {@code target_action} and so it can be
 * flipped by {@code invert} (Apoli's bi-entity {@code actor_action}).
 *
 * <p>JSON: {@code { "type":"pheno:actor_action",
 * "action":{ "type":"pheno:heal", "amount":4 } }}</p>
 */
public final class ActorActionType implements ActionType {

    public static final String KEY = "pheno:actor_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return inner::run;
    }
}
