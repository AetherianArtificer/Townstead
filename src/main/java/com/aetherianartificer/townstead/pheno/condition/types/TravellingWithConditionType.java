package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.journey.Companions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.google.gson.JsonObject;

/**
 * True when the entity is travelling with someone ({@code pheno:travel_with}). In a context with
 * an {@code other} (a story's player), only when travelling with that one.
 * <pre>{ "type": "pheno:travelling_with" }</pre>
 */
public final class TravellingWithConditionType implements com.aetherianartificer.townstead.pheno.condition.ConditionType {
    public static final String KEY = "pheno:travelling_with";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        return ctx -> ctx.entity() != null && Companions.leader(ctx.entity()) != null
                && (ctx.other() == null || Companions.travelsWith(ctx.entity(), ctx.other()));
    }
}
