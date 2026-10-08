package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.data.ScalarData;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** Reads a numeric key from the persistent data of the focused block entity. */
public final class BlockDataValueType implements ValueType {
    public static final String KEY = "pheno:block_data";

    @Override public String key() { return KEY; }

    @Override public Value parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        double fallback = GsonHelper.getAsDouble(json, "default", 0);
        if (key.isBlank()) return null;
        return context -> {
            if (context.level() == null) return fallback;
            var entity = context.level().getBlockEntity(context.focusBlock());
            return entity == null ? fallback
                    : ScalarData.number(entity.getPersistentData(), key, fallback);
        };
    }
}
