package com.aetherianartificer.townstead.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EmoteReactionSchemaValidator {
    private EmoteReactionSchemaValidator() {}

    public static List<String> validate(JsonObject json) {
        List<String> errors = new ArrayList<>();
        if (json == null) {
            errors.add("Reaction json is missing");
            return errors;
        }

        JsonArray triggers = readTriggers(json);
        if (triggers.isEmpty()) {
            errors.add("At least one trigger_emote or trigger_emotes entry is required");
        } else {
            for (JsonElement element : triggers) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    errors.add("Trigger entries must be strings");
                    continue;
                }
                String trigger = element.getAsString().trim();
                if (trigger.isEmpty()) {
                    errors.add("Trigger entries must not be blank");
                }
            }
        }

        int radius = getInt(json, "radius", 8);
        if (radius < 1 || radius > 32) {
            errors.add("radius must be between 1 and 32");
        }

        int cooldown = getInt(json, "cooldown_ticks", 200);
        if (cooldown < 0) {
            errors.add("cooldown_ticks must be >= 0");
        }

        JsonArray candidates = getArray(json, "candidates");
        if (candidates.isEmpty()) {
            errors.add("At least one candidate is required");
            return errors;
        }

        for (int i = 0; i < candidates.size(); i++) {
            JsonElement element = candidates.get(i);
            if (!element.isJsonObject()) {
                errors.add("Candidate " + i + " must be an object");
                continue;
            }
            JsonObject candidate = element.getAsJsonObject();
            String villagerEmote = getString(candidate, "villager_emote", "").trim();
            String chatKeyPrefix = getString(candidate, "chat_key_prefix", "").trim();
            if (villagerEmote.isEmpty() && chatKeyPrefix.isEmpty()) {
                errors.add("Candidate " + i + " must define villager_emote or chat_key_prefix");
            }
            if (!chatKeyPrefix.isEmpty() && !chatKeyPrefix.startsWith("dialogue.chat.")) {
                errors.add("Candidate " + i + " chat_key_prefix must start with dialogue.chat.");
            }
            int chatVariants = getInt(candidate, "chat_variants", 0);
            if (chatVariants < 0) {
                errors.add("Candidate " + i + " chat_variants must be >= 0");
            }
            double chatChance = getDouble(candidate, "chat_chance", 0.35d);
            if (chatChance < 0d || chatChance > 1d) {
                errors.add("Candidate " + i + " chat_chance must be between 0 and 1");
            }
            int weight = getInt(candidate, "weight", 1);
            if (weight <= 0) {
                errors.add("Candidate " + i + " weight must be > 0");
            }

            JsonObject personalityWeights = getObject(candidate, "personality_weights");
            for (String key : personalityWeights.keySet()) {
                String enumName = key.trim().toUpperCase(Locale.ROOT);
                if (!enumName.matches("[A-Z0-9_]+")) {
                    errors.add("Candidate " + i + " has invalid personality key '" + key + "'");
                }
                if (!personalityWeights.get(key).isJsonPrimitive() || !personalityWeights.get(key).getAsJsonPrimitive().isNumber()) {
                    errors.add("Candidate " + i + " personality weight for '" + key + "' must be numeric");
                }
            }
        }

        String requiredMod = getString(json, "required_mod", "").trim();
        if (!requiredMod.isEmpty() && requiredMod.contains(":")) {
            errors.add("required_mod must be a plain mod id, not a resource location");
        }

        return errors;
    }

    static JsonArray readTriggers(JsonObject json) {
        JsonArray triggers = new JsonArray();
        if (json.has("trigger_emote")) {
            JsonElement single = json.get("trigger_emote");
            if (single != null) triggers.add(single);
        }
        JsonArray multiple = getArray(json, "trigger_emotes");
        for (JsonElement element : multiple) {
            triggers.add(element);
        }
        return triggers;
    }

    private static String getString(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        return element.isJsonPrimitive() ? element.getAsString() : fallback;
    }

    private static int getInt(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsInt() : fallback;
    }

    private static double getDouble(JsonObject json, String key, double fallback) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) return fallback;
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber() ? element.getAsDouble() : fallback;
    }

    private static JsonArray getArray(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject getObject(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }
}
