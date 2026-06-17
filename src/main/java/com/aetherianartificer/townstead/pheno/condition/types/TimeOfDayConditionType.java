package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the world time-of-day (ticks, 0-23999) is within {@code [min,max]}.
 *
 * <p>JSON: {@code { "type":"pheno:time_of_day", "min":13000, "max":23000 }}</p>
 */
public final class TimeOfDayConditionType implements ConditionType {

    public static final String KEY = "pheno:time_of_day";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        long min = GsonHelper.getAsLong(json, "min", 0L);
        long max = GsonHelper.getAsLong(json, "max", 24000L);
        return ctx -> {
            long t = Math.floorMod(ctx.level().getDayTime(), 24000L);
            return t >= min && t <= max;
        };
    }
}
