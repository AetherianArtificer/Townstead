package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;

/**
 * The number of targets an {@code on} selection yields, usable anywhere a number is (damage equal
 * to nearby allies, a resource cost scaled by a collection's size). The same
 * {@code townstead_roots:count} id is a condition in a condition slot.
 */
public final class CountValueType implements ValueType {

    public static final String KEY = "pheno:count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        if (json.has("of") && json.get("of").isJsonPrimitive()
                && json.get("of").getAsJsonPrimitive().isString()) {
            String role = json.get("of").getAsString();
            if (role.isBlank()) return null;
            return ctx -> ctx.blockRole(role).size();
        }
        Selector selector = Selectors.parse(json.get("on"));
        if (selector == null) return null;
        return ctx -> selector.select(ctx).size();
    }
}
