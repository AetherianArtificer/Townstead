package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * Compares how many targets an {@code on} selection yields against {@code compare_to} using
 * {@code comparison} (generalizes {@code entity_in_radius} and {@code collection_size}). The same
 * {@code townstead_origins:count} id is a value in a numeric slot.
 */
public final class CountConditionType implements ConditionType {

    public static final String KEY = "pheno:count";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Selector selector = Selectors.parse(json.get("on"));
        if (selector == null) return null;
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 1);
        return ctx -> comparison.compare(selector.select(SelectorContext.of(ctx)).size(), compareTo);
    }
}
