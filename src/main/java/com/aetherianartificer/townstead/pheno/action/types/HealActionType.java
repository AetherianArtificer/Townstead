package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Heals the actor by {@code amount} half-hearts.
 *
 * <p>JSON: {@code { "type":"townstead_origins:heal", "amount":4.0 }}</p>
 */
public final class HealActionType implements ActionType {

    public static final String KEY = "townstead_origins:heal";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float amount = GsonHelper.getAsFloat(json, "amount", 0f);
        if (amount <= 0f) return null;
        return ctx -> ctx.entity().heal(amount);
    }
}
