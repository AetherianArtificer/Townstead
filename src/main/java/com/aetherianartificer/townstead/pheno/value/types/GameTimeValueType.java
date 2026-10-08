package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;

/** The current world's game time, in ticks. */
public final class GameTimeValueType implements ValueType {
    public static final String KEY = "pheno:game_time";

    @Override public String key() { return KEY; }

    @Override public Value parse(JsonObject json) {
        return context -> context.level() == null ? 0 : context.level().getGameTime();
    }
}
