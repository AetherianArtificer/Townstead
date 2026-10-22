package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.combat.Wards;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True while the entity holds a ward ({@code id}, or any) with at least {@code min} health left.
 *
 * <p>JSON: {@code { "type":"pheno:warded", "id":"shell" }}</p>
 */
public final class WardedConditionType implements ConditionType {

    public static final String KEY = "pheno:warded";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String id = json.has("id") ? GsonHelper.getAsString(json, "id") : null;
        float min = GsonHelper.getAsFloat(json, "min", 0.0001f);
        return ctx -> ctx.entity() != null && Wards.remaining(ctx.entity(), id) >= min;
    }
}
