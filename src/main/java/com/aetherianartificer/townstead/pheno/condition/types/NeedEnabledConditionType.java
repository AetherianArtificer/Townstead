package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.api.impl.v1.NeedScales;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * True when a villager need is switched on in this world: {@code hunger}, {@code thirst},
 * {@code energy} or {@code temperature}. It does not depend on where the entity stands.
 * <pre>{ "type": "pheno:need_enabled", "need": "thirst" }</pre>
 */
public final class NeedEnabledConditionType implements ConditionType {
    public static final String KEY = "pheno:need_enabled";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        BooleanSupplier enabled = switch (GsonHelper.getAsString(json, "need", "").toLowerCase(Locale.ROOT)) {
            case "hunger" -> NeedScales::hungerEnabled;
            case "thirst" -> NeedScales::thirstEnabled;
            case "energy", "fatigue" -> NeedScales::fatigueEnabled;
            case "temperature" -> NeedScales::temperatureEnabled;
            default -> null;
        };
        if (enabled == null) return null;
        return ctx -> enabled.getAsBoolean();
    }
}
