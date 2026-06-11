package com.aetherianartificer.townstead.pheno.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Parses a condition JSON element into a {@link Condition}. An object with a
 * {@code "type"} dispatches to the registered {@link ConditionType}; an
 * {@code "inverted":true} field negates the result (matching Apoli). Returns
 * {@code null} for an unknown or malformed condition so callers can choose to
 * skip the whole gated effect rather than apply a half-correct gate.
 */
public final class Conditions {

    public static final Condition ALWAYS = ctx -> true;

    private Conditions() {}

    @Nullable
    public static Condition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        String type = GsonHelper.getAsString(json, "type", "");
        Optional<ConditionType> conditionType = ConditionTypes.get(type);
        if (conditionType.isEmpty()) return null;
        Condition condition = conditionType.get().parse(json);
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }
}
