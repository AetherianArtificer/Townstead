package com.aetherianartificer.townstead.hunger.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

public final class ButcherProfileSchemaValidator {
    private ButcherProfileSchemaValidator() {}

    public static List<String> validate(JsonObject json) {
        List<String> errors = new ArrayList<>();
        String id = GsonHelper.getAsString(json, "id", "").trim();
        if (id.isEmpty()) errors.add("id is required");

        int tier = GsonHelper.getAsInt(json, "required_tier", -1);
        if (tier < 1 || tier > 5) errors.add("required_tier must be in range 1..5");
        int level = GsonHelper.getAsInt(json, "level", tier);
        if (level < 1 || level > 5) errors.add("level must be in range 1..5");
        String family = GsonHelper.getAsString(json, "family", deriveFamilyFromId(id)).trim();
        if (family.isEmpty()) errors.add("family is required");

        if (!json.has("input_filters") || !json.get("input_filters").isJsonObject()) {
            errors.add("input_filters object is required");
        } else {
            validateResourceList(json.getAsJsonObject("input_filters"), "items", "input_filters.items", errors);
            validateResourceList(json.getAsJsonObject("input_filters"), "tags", "input_filters.tags", errors);
        }

        if (!json.has("fuel_filters") || !json.get("fuel_filters").isJsonObject()) {
            errors.add("fuel_filters object is required");
        } else {
            validateResourceList(json.getAsJsonObject("fuel_filters"), "items", "fuel_filters.items", errors);
            validateResourceList(json.getAsJsonObject("fuel_filters"), "tags", "fuel_filters.tags", errors);
        }

        if (!json.has("output_rules") || !json.get("output_rules").isJsonObject()) {
            errors.add("output_rules object is required");
        } else {
            validateResourceList(json.getAsJsonObject("output_rules"), "items", "output_rules.items", errors);
        }

        if (!json.has("throughput_modifiers") || !json.get("throughput_modifiers").isJsonObject()) {
            errors.add("throughput_modifiers object is required");
        }
        if (!json.has("priority_weights") || !json.get("priority_weights").isJsonObject()) {
            errors.add("priority_weights object is required");
        }

        return errors;
    }

    public static String deriveFamilyFromId(String id) {
        if (id == null || id.isBlank()) return ButcherProfileRegistry.DEFAULT_PROFILE_ID;
        String normalized = id.trim();
        int idx = normalized.lastIndexOf("_l");
        if (idx > 0 && idx + 2 < normalized.length()) {
            String suffix = normalized.substring(idx + 2);
            boolean numeric = true;
            for (int i = 0; i < suffix.length(); i++) {
                if (!Character.isDigit(suffix.charAt(i))) {
                    numeric = false;
                    break;
                }
            }
            if (numeric) return normalized.substring(0, idx);
        }
        return normalized;
    }

    private static void validateResourceList(JsonObject parent, String key, String path, List<String> errors) {
        JsonArray list = GsonHelper.getAsJsonArray(parent, key, new JsonArray());
        for (int i = 0; i < list.size(); i++) {
            JsonElement element = list.get(i);
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                errors.add(path + "[" + i + "] must be a string");
                continue;
            }
            String raw = element.getAsString().trim();
            if (raw.isEmpty()) {
                errors.add(path + "[" + i + "] must not be blank");
                continue;
            }
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed == null) {
                errors.add(path + "[" + i + "] is not a valid resource location: " + raw);
            }
        }
    }
}
