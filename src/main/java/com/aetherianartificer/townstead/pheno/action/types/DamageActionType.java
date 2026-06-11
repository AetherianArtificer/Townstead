package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Hurts the actor by {@code amount} (generic damage source) - the cost side of an
 * ability (e.g. a self-sacrifice power).
 *
 * <p>JSON: {@code { "type":"townstead_origins:damage", "amount":2.0 }}</p>
 */
public final class DamageActionType implements ActionType {

    public static final String KEY = "townstead_origins:damage";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        float amount = GsonHelper.getAsFloat(json, "amount", 0f);
        if (amount <= 0f) return null;
        return ctx -> ctx.entity().hurt(ctx.level().damageSources().generic(), amount);
    }
}
