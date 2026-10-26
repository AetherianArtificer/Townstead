package com.aetherianartificer.townstead.dialogue.contextual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A small authored voice palette selected by semantic intent and context rather than random line number. */
public record DialoguePalette(ResourceLocation id, String intent, int minIntervalTicks,
                              int historySize, List<Line> lines) {
    public DialoguePalette {
        intent = normalized(intent);
        minIntervalTicks = Math.max(0, Math.min(24000, minIntervalTicks));
        historySize = Math.max(1, Math.min(64, historySize));
        lines = List.copyOf(lines);
        if (intent.isEmpty()) throw new IllegalArgumentException("intent is required");
        if (lines.isEmpty()) throw new IllegalArgumentException("lines must not be empty");
    }

    public static DialoguePalette parse(ResourceLocation id, JsonObject json) {
        List<Line> lines = new ArrayList<>();
        JsonArray array = GsonHelper.getAsJsonArray(json, "lines");
        int index = 0;
        for (JsonElement element : array) {
            JsonObject line = GsonHelper.convertToJsonObject(element, "lines[" + index + "]");
            String translation = GsonHelper.getAsString(line, "translation", "").trim();
            if (!isTranslationKey(translation)) {
                throw new IllegalArgumentException("lines[" + index + "].translation must be a localization key");
            }
            String lineId = GsonHelper.getAsString(line, "id", translation);
            if (lineId.isBlank() || lines.stream().anyMatch(existing -> existing.key().equals(id + "#" + lineId)))
                throw new IllegalArgumentException("lines[" + index + "].id must be nonempty and unique");
            lines.add(new Line(id + "#" + lineId, translation,
                    Math.max(1, GsonHelper.getAsInt(line, "weight", 1)),
                    strings(line, "context"), strings(line, "personality"),
                    strings(line, "relationship")));
            index++;
        }
        return new DialoguePalette(id, GsonHelper.getAsString(json, "intent", ""),
                GsonHelper.getAsInt(json, "min_interval_ticks", 200),
                GsonHelper.getAsInt(json, "history_size", 8), lines);
    }

    private static Set<String> strings(JsonObject json, String key) {
        Set<String> out = new LinkedHashSet<>();
        if (!json.has(key)) return out;
        for (JsonElement value : GsonHelper.getAsJsonArray(json, key)) {
            String normalized = normalized(value.getAsString());
            if (!normalized.isEmpty()) out.add(normalized);
        }
        return Set.copyOf(out);
    }

    static String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isTranslationKey(String value) {
        return !value.isEmpty() && value.indexOf('.') > 0 && value.chars().noneMatch(Character::isWhitespace);
    }

    public record Line(String key, String translation, int weight, Set<String> context,
                       Set<String> personality, Set<String> relationship) {
        public Line {
            context = Set.copyOf(context); personality = Set.copyOf(personality);
            relationship = Set.copyOf(relationship); weight = Math.max(1, weight);
        }

        boolean eligible(DialogueRequest request) {
            return request.context().containsAll(context)
                    && (personality.isEmpty() || intersects(personality, request.personality()))
                    && (relationship.isEmpty() || intersects(relationship, request.relationship()));
        }

        private static boolean intersects(Set<String> expected, Set<String> actual) {
            for (String value : expected) if (actual.contains(value)) return true;
            return false;
        }
    }
}
