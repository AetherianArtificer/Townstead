package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonObject;

/**
 * Heals the actor by {@code amount} half-hearts. {@code amount} is a value, so it can be a literal
 * or a {@code count} of a selection.
 *
 * <p>JSON: {@code { "type":"pheno:heal", "amount":4.0 }}</p>
 */
public final class HealActionType implements ActionType {

    public static final String KEY = "pheno:heal";

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
            if (a > 0f) ctx.entity().heal(a);
        };
    }
}
