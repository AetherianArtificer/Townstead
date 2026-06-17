package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;

/**
 * Makes the actor stop riding whatever it is mounted on (Apoli's {@code dismount}).
 *
 * <p>JSON: {@code { "type":"pheno:dismount" }}</p>
 */
public final class DismountActionType implements ActionType {

    public static final String KEY = "pheno:dismount";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        return ctx -> ctx.entity().stopRiding();
    }
}
