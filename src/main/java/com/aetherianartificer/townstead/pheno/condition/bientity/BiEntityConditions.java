package com.aetherianartificer.townstead.pheno.condition.bientity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Parses a bi-entity-condition JSON element into a {@link BiEntityCondition}. Dispatches
 * by {@code "type"}; {@code "inverted":true} negates. Clone of {@code Conditions}.
 */
public final class BiEntityConditions {

    private BiEntityConditions() {}

    @Nullable
    public static BiEntityCondition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        Optional<BiEntityConditionType> type = BiEntityConditionTypes.get(GsonHelper.getAsString(json, "type", ""));
        if (type.isEmpty()) return null;
        BiEntityCondition condition = type.get().parse(json);
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }
}
