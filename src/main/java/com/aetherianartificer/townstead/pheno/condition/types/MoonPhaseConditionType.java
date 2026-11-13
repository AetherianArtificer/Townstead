package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the moon phase is within {@code [min,max]}. Phases count from 0 (full moon) through
 * 4 (new moon) to 7, the same numbers the game uses.
 *
 * <p>JSON: {@code { "type":"pheno:moon_phase", "min":0, "max":0 }}</p>
 */
public final class MoonPhaseConditionType implements ConditionType {

    public static final String KEY = "pheno:moon_phase";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        int min = GsonHelper.getAsInt(json, "min", 0);
        int max = GsonHelper.getAsInt(json, "max", 7);
        return ctx -> {
            int phase = ctx.level().getMoonPhase();
            return phase >= min && phase <= max;
        };
    }
}
