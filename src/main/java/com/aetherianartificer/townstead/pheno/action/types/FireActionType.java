package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Sets or clears the actor's fire. One instance is registered per mode
 * ({@code ignite} for {@code seconds}, {@code extinguish}). {@code setRemainingFireTicks}
 * is used directly so the call is identical on both branches.
 *
 * <p>JSON: {@code { "type":"townstead_origins:ignite", "seconds":3 }}</p>
 */
public final class FireActionType implements ActionType {

    public static final String IGNITE_KEY = "townstead_origins:ignite";
    public static final String EXTINGUISH_KEY = "townstead_origins:extinguish";

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
        if (!ignite) return ctx -> ctx.entity().setRemainingFireTicks(0);
        int ticks = GsonHelper.getAsInt(json, "seconds", 3) * 20;
        return ctx -> ctx.entity().setRemainingFireTicks(ticks);
    }
}
