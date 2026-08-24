package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Basic arithmetic over ordinary Pheno values. */
public final class ArithmeticValueType implements ValueType {
    public static final String KEY = "pheno:arithmetic";

    @Override public String key() { return KEY; }

    @Override public Value parse(JsonObject json) {
        if (!json.has("values") || !json.get("values").isJsonArray()) return null;
        List<Value> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("values")) {
            Value value = Values.parse(element);
            if (value == null) return null;
            values.add(value);
        }
        if (values.isEmpty()) return null;
        String operation = GsonHelper.getAsString(json, "operation", "add").toLowerCase(Locale.ROOT);
        if (!List.of("add", "subtract", "multiply", "divide", "min", "max").contains(operation)) {
            return null;
        }
        return context -> {
            double result = values.get(0).get(context);
            for (int i = 1; i < values.size(); i++) {
                double next = values.get(i).get(context);
                result = switch (operation) {
                    case "subtract" -> result - next;
                    case "multiply" -> result * next;
                    case "divide" -> next == 0 ? result : result / next;
                    case "min" -> Math.min(result, next);
                    case "max" -> Math.max(result, next);
                    default -> result + next;
                };
            }
            return result;
        };
    }
}
