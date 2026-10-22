package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.rest.RestEvents;
import com.google.gson.JsonObject;

/**
 * {@code pheno:wake}: wakes the entity if it is asleep.
 *
 * <p>JSON: {@code { "type":"pheno:wake" }}</p>
 */
public final class WakeActionType implements ActionType {
    public static final String KEY = "pheno:wake";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> RestEvents.wake(ctx.entity());
    }
}
