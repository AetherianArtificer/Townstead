package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.reservation.ReservationSpec;
import com.google.gson.JsonObject;

/** Acquires an exclusive reservation on the current action target for this execution scope. */
public final class ReserveActionType implements ActionType {
    public static final String KEY = "pheno:reserve";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        if (spec(json) == null) return null;
        Action continuation = json.has("action") ? Actions.parse(json.get("action")) : null;
        if (json.has("action") && continuation == null) return null;
        return ctx -> {
            if (ctx.reservations() == null || !ctx.reservations().reserve(ctx.entity())) {
                ctx.fail();
                return;
            }
            if (continuation != null) continuation.run(ctx);
        };
    }

    /** Shared compiler entry for long-lived hosts such as a workstation task. */
    public static ReservationSpec spec(JsonObject json) {
        return ReservationSpec.parse(json);
    }
}
