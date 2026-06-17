package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * True when the entity's health is within {@code [min,max]}. With
 * {@code "relative":true} the bounds are a fraction of max health (0-1).
 *
 * <p>JSON: {@code { "type":"pheno:health", "max":0.5, "relative":true }}</p>
 */
public final class HealthConditionType implements ConditionType {

    public static final String KEY = "pheno:health";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        float min = GsonHelper.getAsFloat(json, "min", 0f);
        float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
        boolean relative = GsonHelper.getAsBoolean(json, "relative", false);
        return ctx -> {
            LivingEntity e = ctx.entity();
            float value = relative ? (e.getMaxHealth() <= 0f ? 0f : e.getHealth() / e.getMaxHealth()) : e.getHealth();
            return value >= min && value <= max;
        };
    }
}
