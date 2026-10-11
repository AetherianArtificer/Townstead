package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.combat.Wards;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Removes the focus's ward {@code id}, or every ward when {@code id} is omitted.
 *
 * <p>JSON: {@code { "type":"pheno:clear_ward", "id":"shell" }}</p>
 */
public final class ClearWardActionType implements ActionType {

    public static final String KEY = "pheno:clear_ward";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String id = json.has("id") ? GsonHelper.getAsString(json, "id") : null;
        return ctx -> Wards.clear(ctx.entity(), id);
    }
}
