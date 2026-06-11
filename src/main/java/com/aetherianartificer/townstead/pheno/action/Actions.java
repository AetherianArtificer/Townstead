package com.aetherianartificer.townstead.pheno.action;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses an action JSON element into an {@link Action}. An array runs every action
 * in order; an object dispatches by {@code "type"} to a registered
 * {@link ActionType}. Returns {@code null} for an unknown or malformed action so a
 * gene with no usable action is rejected.
 */
public final class Actions {

    private Actions() {}

    @Nullable
    public static Action parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            List<Action> actions = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                Action action = parse(child);
                if (action == null) return null;
                actions.add(action);
            }
            if (actions.isEmpty()) return null;
            return ctx -> actions.forEach(a -> a.run(ctx));
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        Optional<ActionType> type = ActionTypes.get(GsonHelper.getAsString(json, "type", ""));
        return type.map(t -> t.parse(json)).orElse(null);
    }
}
