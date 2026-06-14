package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Sets or clears the actor's fire. One instance is registered per mode
 * ({@code ignite} for {@code seconds}, {@code extinguish}). Ignite uses the canonical
 * "set on fire" call ({@code igniteForSeconds} on 1.21, {@code setSecondsOnFire} on 1.20)
 * so the entity actually burns (visual + damage), rather than poking the raw fire-tick
 * counter; both take whole seconds, so there is no tick conversion to drift.
 *
 * <p>JSON: {@code { "type":"pheno:ignite", "seconds":3 }}</p>
 */
public final class FireActionType implements ActionType {

    public static final String IGNITE_KEY = "pheno:ignite";
    public static final String EXTINGUISH_KEY = "pheno:extinguish";

    private final String key;
    private final boolean ignite;

    public FireActionType(String key, boolean ignite) {
        this.key = key;
        this.ignite = ignite;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Action parse(JsonObject json) {
        if (!ignite) return ctx -> ctx.entity().clearFire();
        int seconds = Math.max(1, GsonHelper.getAsInt(json, "seconds", 3));
        return ctx -> {
            //? if neoforge {
            ctx.entity().igniteForSeconds(seconds);
            //?} else {
            /*ctx.entity().setSecondsOnFire(seconds);
            *///?}
        };
    }
}
