package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.reservation.Reservations;
import com.google.gson.JsonObject;

/** True when the current entity is exclusively reserved by any live execution scope. */
public final class ReservedConditionType implements ConditionType {
    public static final String KEY = "pheno:reserved";

    @Override public String key() { return KEY; }

    @Override public Condition parse(JsonObject json) {
        return ctx -> Reservations.isReserved(ctx.entity());
    }
}
