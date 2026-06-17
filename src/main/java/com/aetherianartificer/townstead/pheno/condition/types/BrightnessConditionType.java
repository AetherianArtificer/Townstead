package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the light level at the entity is within {@code [min,max]} (0-15).
 *
 * <p>JSON: {@code { "type":"pheno:brightness", "min":0, "max":7 }}</p>
 */
public final class BrightnessConditionType implements ConditionType {

    public static final String KEY = "pheno:brightness";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        int min = GsonHelper.getAsInt(json, "min", 0);
        int max = GsonHelper.getAsInt(json, "max", 15);
        return ctx -> {
            int b = ctx.level().getMaxLocalRawBrightness(ctx.pos());
            return b >= min && b <= max;
        };
    }
}
