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

    /** A literal: the same number for an entity or a subject alike. */
    public static Value constant(double value) {
        return Value.subjectAware(ctx -> value);
    }

    @Nullable
    public static Value parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return constant(element.getAsDouble());
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            return ValueTypes.get(GsonHelper.getAsString(obj, "type", "")).map(t -> t.parse(obj)).orElse(null);
        }
        return null;
    }
}
