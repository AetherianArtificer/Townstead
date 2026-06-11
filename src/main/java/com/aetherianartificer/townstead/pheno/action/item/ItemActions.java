package com.aetherianartificer.townstead.pheno.action.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses an item-action JSON element into an {@link ItemAction}. An array runs every
 * action in order; an object dispatches by {@code "type"}. Clone of {@code Actions}.
 */
public final class ItemActions {

    private ItemActions() {}

    @Nullable
    public static ItemAction parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            List<ItemAction> actions = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                ItemAction action = parse(child);
                if (action == null) return null;
                actions.add(action);
            }
            if (actions.isEmpty()) return null;
            return ctx -> actions.forEach(a -> a.run(ctx));
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        Optional<ItemActionType> type = ItemActionTypes.get(GsonHelper.getAsString(json, "type", ""));
        return type.map(t -> t.parse(json)).orElse(null);
    }
}
