package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.aetherianartificer.townstead.pheno.action.item.ItemActions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs each item action in {@code actions}, in order, on the same stack: the item-domain twin of
 * the entity {@code pheno:and}.
 *
 * <p>JSON: {@code { "type":"pheno:and", "actions":[ {...}, {...} ] }}</p>
 */
public final class AndItemActionType implements ItemActionType {

    public static final String KEY = "pheno:and";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public ItemAction parse(JsonObject json) {
        if (!json.has("actions") || !json.get("actions").isJsonArray()) return null;
        List<ItemAction> actions = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("actions")) {
            ItemAction action = ItemActions.parse(element);
            if (action == null) return null;
            actions.add(action);
        }
        return ctx -> {
            for (ItemAction action : actions) action.run(ctx);
        };
    }
}
