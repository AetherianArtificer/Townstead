package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Saves the other entity of the action (for a gift, the giver) on the entity under {@code key}, for
 * whatever reads it later, such as who began a cure.
 *
 * <pre>
 * { "type": "pheno:remember", "key": "townstead:cured_by" }
 * </pre>
 */
public final class RememberActionType implements ActionType {
    public static final String KEY = "pheno:remember";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key");
        return ctx -> {
            if (ctx.other() == null) {
                ctx.fail();
                return;
            }
            ctx.entity().getPersistentData().putUUID(key, ctx.other().getUUID());
        };
    }
}
