package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Changes the actor's air supply by {@code air} ticks, clamped to its maximum (Apoli's
 * {@code gain_air}). Negative values drain air.
 *
 * <p>JSON: {@code { "type":"pheno:gain_air", "air":60 }}</p>
 */
public final class GainAirActionType implements ActionType {

    public static final String KEY = "pheno:gain_air";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        int air = GsonHelper.getAsInt(json, "air", 0);
        if (air == 0) return null;
        return ctx -> {
            int next = Math.min(ctx.entity().getMaxAirSupply(), ctx.entity().getAirSupply() + air);
            ctx.entity().setAirSupply(next);
        };
    }
}
