package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;

/**
 * Runs the wrapped action only on the named {@code side} (Apoli's meta {@code side}).
 * Townstead runs all power actions server-side, so {@code server} (default) runs and
 * {@code client} is a no-op (client-only actions are not modeled).
 *
 * <p>JSON: {@code { "type":"pheno:side", "side":"server", "action":{...} }}</p>
 */
public final class SideActionType implements ActionType {

    public static final String KEY = "pheno:side";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        boolean server = !"client".equals(GsonHelper.getAsString(json, "side", "server").toLowerCase(Locale.ROOT));
        Action inner = Actions.parse(json.get("action"));
        if (inner == null) return null;
        return ctx -> {
            if (server) inner.run(ctx);
        };
    }
}
