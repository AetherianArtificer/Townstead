package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;

/** Explicitly releases every reservation owned by the current execution scope. */
public final class ReleaseActionType implements ActionType {
    public static final String KEY = "pheno:release";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> {
            if (ctx.reservations() == null) ctx.fail();
            else ctx.reservations().release();
        };
    }
}
