package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Toggles the actor's no-gravity flag (Apugli's {@code set_no_gravity}). {@code gravity}
 * is whether gravity stays ON; the default {@code false} disables gravity (the entity
 * floats), pair it with a condition or a counter-action to restore it.
 *
 * <p>JSON: {@code { "type":"pheno:set_no_gravity", "gravity":false }}</p>
 */
public final class SetNoGravityActionType implements ActionType {

    public static final String KEY = "pheno:set_no_gravity";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        boolean gravity = GsonHelper.getAsBoolean(json, "gravity", false);
        return ctx -> ctx.entity().setNoGravity(!gravity);
    }
}
