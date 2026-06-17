package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonObject;

/**
 * Runs every action in {@code actions} in order (Apoli's meta {@code and}). A bare JSON
 * array does the same; this is the named form.
 *
 * <p>JSON: {@code { "type":"pheno:and", "actions":[ {...}, {...} ] }}</p>
 */
public final class AndActionType implements ActionType {

    public static final String KEY = "pheno:and";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Action inner = Actions.parse(json.get("actions"));
        if (inner == null) return null;
        return inner::run;
    }
}
