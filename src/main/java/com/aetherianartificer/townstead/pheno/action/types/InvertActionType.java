package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;

/**
 * Runs the wrapped bi-entity action with the actor and target swapped (Apoli's
 * bi-entity {@code invert}). No-op when there is no counterpart.
 *
 * <p>JSON: {@code { "type":"townstead_origins:invert",
 * "action":{ "type":"townstead_origins:mount" } }}</p>
 */
public final class InvertActionType implements ActionType {

    public static final String KEY = "townstead_origins:invert";

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
