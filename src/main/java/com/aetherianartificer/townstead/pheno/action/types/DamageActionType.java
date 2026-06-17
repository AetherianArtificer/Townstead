package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;

/**
 * Hurts the actor by {@code amount} (generic damage source) - the cost side of an
 * ability (e.g. a self-sacrifice power). {@code amount} is a value, so it can be a literal or a
 * {@code count} of a selection.
 *
 * <p>JSON: {@code { "type":"pheno:damage", "amount":2.0 }}</p>
 */
public final class DamageActionType implements ActionType {

    public static final String KEY = "pheno:damage";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Value amount = json.has("amount") ? Values.parse(json.get("amount")) : null;
        if (amount == null) return null;
        return ctx -> {
            float a = (float) amount.get(SelectorContext.of(ctx));
            if (a > 0f) ctx.entity().hurt(ctx.level().damageSources().generic(), a);
        };
    }
}
