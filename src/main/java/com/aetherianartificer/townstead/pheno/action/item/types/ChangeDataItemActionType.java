package com.aetherianartificer.townstead.pheno.action.item.types;

import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.aetherianartificer.townstead.pheno.data.ScalarData;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** Sets, adds to, or removes one scalar custom-data key on an item stack. */
public final class ChangeDataItemActionType implements ItemActionType {
    public static final String KEY = "pheno:change_data";

    @Override public String key() { return KEY; }

    @Override public ItemAction parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        String operation = GsonHelper.getAsString(json, "operation", "set").toLowerCase(java.util.Locale.ROOT);
        JsonElement value = ScalarData.scalar(json.get("value"));
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        if (key.isBlank() || (!"remove".equals(operation) && value == null)
                || ("add".equals(operation) && !value.getAsJsonPrimitive().isNumber())
                || !java.util.List.of("set", "add", "remove").contains(operation)) return null;
        return context -> ScalarData.updateItem(context.stack(), tag -> {
            if ("remove".equals(operation)) {
                tag.remove(key);
            } else if ("add".equals(operation)) {
                double changed = Math.max(min, Math.min(max, tag.getDouble(key) + value.getAsDouble()));
                tag.putDouble(key, changed);
            } else {
                ScalarData.put(tag, key, value, 0);
            }
        });
    }
}
