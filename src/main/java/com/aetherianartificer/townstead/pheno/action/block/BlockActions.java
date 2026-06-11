package com.aetherianartificer.townstead.pheno.action.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses a block-action JSON element into a {@link BlockAction}. An array runs every
 * action in order; an object dispatches by {@code "type"}. Clone of {@code Actions}.
 */
public final class BlockActions {

    private BlockActions() {}

    @Nullable
    public static BlockAction parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            List<BlockAction> actions = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                BlockAction action = parse(child);
                if (action == null) return null;
                actions.add(action);
            }
            if (actions.isEmpty()) return null;
            return ctx -> actions.forEach(a -> a.run(ctx));
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        Optional<BlockActionType> type = BlockActionTypes.get(GsonHelper.getAsString(json, "type", ""));
        return type.map(t -> t.parse(json)).orElse(null);
    }
}
