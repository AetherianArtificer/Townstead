package com.aetherianartificer.townstead.pheno.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Parses a numeric field into a {@link Value}: a JSON number is a constant, an object dispatches
 * by {@code "type"} to a registered {@link ValueType} (e.g. {@code count}). Returns {@code null}
 * for a malformed value so the carrying action is rejected.
 */
public final class Values {

    private Values() {}

    public static Value constant(double value) {
        return ctx -> value;
    }

    @Nullable
    public static Value parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            return ctx -> value;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            return ValueTypes.get(GsonHelper.getAsString(obj, "type", "")).map(t -> t.parse(obj)).orElse(null);
        }
        return null;
    }
}
