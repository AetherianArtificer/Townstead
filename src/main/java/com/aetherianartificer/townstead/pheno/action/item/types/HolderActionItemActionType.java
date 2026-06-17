package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.google.gson.JsonObject;

/**
 * Runs an entity action on the stack's holder (Apoli's item {@code holder_action}): the
 * bridge from an item context back to an entity action. No-op without a holder.
 *
 * <p>JSON: {@code { "type":"pheno:holder_action",
 * "action":{ "type":"pheno:damage", "amount":2 } }}</p>
 */
public final class HolderActionItemActionType implements ItemActionType {

    public static final String KEY = "pheno:holder_action";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            if (ctx.holder() != null) inner.run(new ActionContext(ctx.holder()));
        };
    }
}
