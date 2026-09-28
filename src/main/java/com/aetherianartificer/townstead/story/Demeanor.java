package com.aetherianartificer.townstead.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * How a villager is treating the player right now, as one word Ink can test with
 * {@code demeanor()}. Bands are tried in order and the first that matches wins; a band with no
 * rules always matches. Each rule bounds one relationship-ledger quality the villager holds
 * toward the player, on the ledger's -100 to 100 scale. A story or Persona can replace the
 * default bands under {@code "demeanor"}:
 * <pre>
 * "demeanor": [
 *   { "name": "stern",   "any": { "resentment": { "min": 40 }, "fear": { "min": 30 } } },
 *   { "name": "jovial",  "all": { "affection": { "min": 30 }, "resentment": { "max": 9 } } },
 *   { "name": "neutral" }
 * ]
 * </pre>
 */
public final class Demeanor {
    private Demeanor() {}

    private record Rule(String quality, double min, double max) {
        boolean holds(ToDoubleFunction<String> value) {
            double v = value.applyAsDouble(quality);
            return v >= min && v <= max;
        }
    }

    public record Band(String name, boolean any, List<Rule> rules) {
        boolean matches(ToDoubleFunction<String> value) {
            if (rules.isEmpty()) return true;
            return any ? rules.stream().anyMatch(r -> r.holds(value)) : rules.stream().allMatch(r -> r.holds(value));
        }
    }

    /** The bands from the Village Builder design; most severe first. */
    public static final List<Band> DEFAULT = List.of(
            new Band("stern", true, List.of(rule("resentment", 40, 100), rule("fear", 30, 100))),
            new Band("guarded", true, List.of(rule("resentment", 20, 100), rule("trust", -100, -0.001))),
            new Band("jovial", false, List.of(rule("affection", 30, 100), rule("resentment", -100, 9.999))),
            new Band("warm", false, List.of(rule("trust", 20, 100))),
            new Band("neutral", false, List.of()));

    public static String of(List<Band> bands, ToDoubleFunction<String> quality) {
        for (Band band : bands) if (band.matches(quality)) return band.name();
        return "neutral";
    }

    /** Reads {@code "demeanor"} bands, or returns null (with {@code issues} filled) when they are malformed. */
    public static List<Band> parse(JsonElement json, List<String> issues) {
        if (!json.isJsonArray()) {
            issues.add("error: demeanor must be a list of bands");
            return null;
        }
        List<Band> bands = new ArrayList<>();
        for (JsonElement element : (JsonArray) json) {
            if (!element.isJsonObject() || !element.getAsJsonObject().has("name")) {
                issues.add("error: each demeanor band needs a \"name\"");
                return null;
            }
            JsonObject band = element.getAsJsonObject();
            boolean any = band.has("any");
            JsonObject rules = any ? band.getAsJsonObject("any") : band.has("all") ? band.getAsJsonObject("all") : new JsonObject();
            List<Rule> parsed = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : rules.entrySet()) {
                JsonObject bounds = entry.getValue().isJsonObject() ? entry.getValue().getAsJsonObject() : new JsonObject();
                double min = bounds.has("min") ? bounds.get("min").getAsDouble() : -Double.MAX_VALUE;
                double max = bounds.has("max") ? bounds.get("max").getAsDouble() : Double.MAX_VALUE;
                parsed.add(new Rule(entry.getKey().toLowerCase(Locale.ROOT), min, max));
            }
            bands.add(new Band(band.get("name").getAsString().toLowerCase(Locale.ROOT), any, List.copyOf(parsed)));
        }
        return List.copyOf(bands);
    }

    private static Rule rule(String quality, double min, double max) {
        return new Rule(quality, min, max);
    }
}
