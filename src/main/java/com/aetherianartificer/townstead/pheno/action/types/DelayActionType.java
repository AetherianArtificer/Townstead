package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionScheduler;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Runs the wrapped action {@code ticks} server ticks later (Apoli's meta {@code delay}),
 * via {@link ActionScheduler}. The action is skipped if the actor is gone by then.
 *
 * <p>JSON: {@code { "type":"pheno:delay", "ticks":40,
 * "action":{ "type":"pheno:explode", "power":3 } }}</p>
 */
public final class DelayActionType implements ActionType {

    public static final String KEY = "pheno:delay";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int ticks = Math.max(0, GsonHelper.getAsInt(json, "ticks", 0));
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> ActionScheduler.schedule(ctx, ticks, inner);
    }
}
