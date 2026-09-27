package com.aetherianartificer.townstead.story.goal;

import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds {@link Goal}s from data. A goal is a JSON object, written inline in a story's
 * {@code story.json} under {@code goals} or shared in {@code data/<ns>/goal/<id>.json}:
 * <pre>
 * { "text": "Put a Field Post by {teller}'s fields",
 *   "condition": { "type": "pheno:in_work_area", "area": "townstead:field_post", "at": "job_site" } }
 *
 * { "text": "{teller} harvests {count} crops", "count": 16,
 *   "event": "townstead:work_completed", "where": { "worker": "teller", "verb": "townstead:harvested" } }
 *
 * { "params": ["profession", "count"], "text": "Have {count} {profession}s",
 *   "value": { "type": "pheno:count", "on": { "type": "pheno:village",
 *              "where": { "type": "pheno:profession", "profession": "$profession" } } },
 *   "target": "$count" }
 * </pre>
 * {@code subject} is {@code teller} (default) or {@code player}. A story names a goal in Ink as
 * {@code # goal: field_post} or, with parameters, {@code # goal: have_profession(farmer, 3)}.
 */
public final class Goals {
    private Goals() {}

    private static final Pattern REF = Pattern.compile("\\s*([A-Za-z0-9_.\\-:/]+)\\s*(?:\\((.*)\\))?\\s*");

    private static volatile Map<ResourceLocation, JsonObject> library = Map.of();

    public static void setLibrary(Map<ResourceLocation, JsonObject> next) {
        library = Map.copyOf(next);
    }

    public static Map<ResourceLocation, JsonObject> library() {
        return library;
    }

    public record Parsed(@Nullable Goal goal, @Nullable String error) {
        static Parsed fail(String error) { return new Parsed(null, error); }
    }

    /**
     * Resolves a reference such as {@code have_profession(farmer, 3)}: a plain name is looked up in
     * the story's own goals first, then in the library under the story's namespace, then
     * {@code townstead}.
     */
    public static Parsed resolve(String reference, String namespace, Map<String, JsonObject> inline) {
        Matcher m = REF.matcher(reference);
        if (!m.matches()) return Parsed.fail("'" + reference + "' is not a goal name");
        String name = m.group(1).toLowerCase(Locale.ROOT);
        List<String> args = splitArgs(m.group(2));
        JsonObject template = name.contains(":") ? null : inline.get(name);
        if (template == null) {
            for (String candidate : name.contains(":") ? List.of(name) : List.of(namespace + ":" + name, "townstead:" + name)) {
                ResourceLocation id = ResourceLocation.tryParse(candidate);
                if (id != null && library.containsKey(id)) {
                    template = library.get(id);
                    break;
                }
            }
        }
        if (template == null) return Parsed.fail("no goal named '" + name + "' in story.json or data/*/goal/");
        return build(template, args);
    }

    static Parsed build(JsonObject template, List<String> args) {
        List<String> params = new ArrayList<>();
        if (template.has("params") && template.get("params").isJsonArray()) {
            for (JsonElement p : template.getAsJsonArray("params")) params.add(p.getAsString());
        }
        if (params.size() != args.size()) {
            return Parsed.fail("expects " + params.size() + " values" + (params.isEmpty() ? "" : " (" + String.join(", ", params) + ")")
                    + " but got " + args.size());
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < params.size(); i++) values.put(params.get(i), args.get(i));
        JsonObject json = substitute(template.deepCopy(), values).getAsJsonObject();

        if (!json.has("text")) return Parsed.fail("a goal needs \"text\", the line shown in the Quest Ledger");
        String text = json.get("text").getAsString();
        for (Map.Entry<String, String> value : values.entrySet()) text = text.replace("{" + value.getKey() + "}", value.getValue());
        Goal.Subject subject;
        try {
            subject = Goal.Subject.valueOf(json.has("subject") ? json.get("subject").getAsString().toUpperCase(Locale.ROOT) : "TELLER");
        } catch (IllegalArgumentException e) {
            return Parsed.fail("subject must be \"teller\" or \"player\"");
        }
        try {
            if (json.has("event")) {
                Class<? extends TownsteadEvent> event = GoalEvents.byId(json.get("event").getAsString());
                if (event == null) return Parsed.fail("no event '" + json.get("event").getAsString() + "'");
                Condition condition = null;
                if (json.has("condition")) {
                    condition = Conditions.parse(json.get("condition"));
                    if (condition == null) return Parsed.fail("condition is not a known Pheno condition");
                }
                EventMatcher matcher = EventMatcher.parse(event,
                        json.has("where") ? json.getAsJsonObject("where") : null,
                        json.has("entity") ? json.get("entity").getAsString() : null, condition);
                long count = json.has("count") ? json.get("count").getAsLong() : 1L;
                return new Parsed(new Goal(text, count, subject, null, null, event, matcher), null);
            }
            if (json.has("condition")) {
                Condition condition = Conditions.parse(json.get("condition"));
                if (condition == null) return Parsed.fail("condition is not a known Pheno condition");
                return new Parsed(new Goal(text, 1, subject, condition, null, null, null), null);
            }
            if (json.has("value")) {
                Value value = Values.parse(json.get("value"));
                if (value == null) return Parsed.fail("value is not a known Pheno value");
                if (!json.has("target")) return Parsed.fail("a value goal needs \"target\"");
                return new Parsed(new Goal(text, (long) Math.ceil(json.get("target").getAsDouble()), subject,
                        null, value, null, null), null);
            }
        } catch (RuntimeException e) {
            return Parsed.fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return Parsed.fail("a goal needs one of \"condition\", \"value\" or \"event\"");
    }

    /** Replaces {@code $param} in every string. A string that is only {@code $param} takes the value's type. */
    static JsonElement substitute(JsonElement element, Map<String, String> values) {
        if (values.isEmpty()) return element;
        if (element.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (Map.Entry<String, JsonElement> e : element.getAsJsonObject().entrySet()) out.add(e.getKey(), substitute(e.getValue(), values));
            return out;
        }
        if (element.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement e : element.getAsJsonArray()) out.add(substitute(e, values));
            return out;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return element;
        String text = element.getAsString();
        for (Map.Entry<String, String> value : values.entrySet()) {
            if (text.equals("$" + value.getKey())) return literal(value.getValue());
        }
        List<String> keys = new ArrayList<>(values.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        for (String key : keys) text = text.replace("$" + key, values.get(key));
        return new JsonPrimitive(text);
    }

    private static JsonElement literal(String value) {
        try {
            return new JsonPrimitive(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
        }
        try {
            return new JsonPrimitive(Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
        }
        if (value.equals("true") || value.equals("false")) return new JsonPrimitive(Boolean.parseBoolean(value));
        return new JsonPrimitive(value);
    }

    private static List<String> splitArgs(@Nullable String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null || raw.isBlank()) return out;
        for (String part : raw.split(",")) out.add(part.trim());
        return out;
    }
}
