package com.aetherianartificer.townstead.persona;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Name pools a Persona rolls from once per world: given names by gender, a family name, and extra
 * names that belong to the story (a parent's house). The rolled names are Ink variables:
 * {@code given_name}, {@code family_name}, and each extra by its key.
 * <pre>
 * "names": {
 *   "given": { "female": ["Adelheid", "Ilona"], "male": ["Richter", "Simon"] },
 *   "family": ["Roth", "Farkas"],
 *   "extra": { "house": ["von Aschenau", "von Stillbach"] }
 * }
 * </pre>
 */
public record PersonaNames(Map<String, List<String>> given, List<String> family, Map<String, List<String>> extra) {
    public static final String GIVEN = "given_name";
    public static final String FAMILY = "family_name";

    /** A given name for {@code gender} ("female" or "male"), falling back to {@code any}; null when there is none. */
    public @Nullable String pickGiven(String gender, RandomSource random) {
        List<String> pool = given.getOrDefault(gender, given.get("any"));
        return pick(pool, random);
    }

    public static @Nullable String pick(@Nullable List<String> pool, RandomSource random) {
        return pool == null || pool.isEmpty() ? null : pool.get(random.nextInt(pool.size()));
    }

    static @Nullable PersonaNames parse(JsonElement json, List<String> issues) {
        if (!json.isJsonObject()) {
            issues.add("error: names must be an object");
            return null;
        }
        JsonObject root = json.getAsJsonObject();
        Map<String, List<String>> given = new LinkedHashMap<>();
        if (root.has("given")) {
            JsonElement raw = root.get("given");
            if (raw.isJsonArray()) {
                given.put("any", strings(raw));
            } else if (raw.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
                    String key = entry.getKey();
                    if (!key.equals("female") && !key.equals("male") && !key.equals("any")) {
                        issues.add("error: names.given: use \"female\", \"male\" or \"any\", not '" + key + "'");
                        continue;
                    }
                    given.put(key, strings(entry.getValue()));
                }
            }
        }
        List<String> family = root.has("family") ? strings(root.get("family")) : List.of();
        Map<String, List<String>> extra = new LinkedHashMap<>();
        if (root.has("extra") && root.get("extra").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("extra").entrySet()) {
                extra.put(entry.getKey(), strings(entry.getValue()));
            }
        }
        return new PersonaNames(Map.copyOf(given), List.copyOf(family), Map.copyOf(extra));
    }

    private static List<String> strings(JsonElement json) {
        List<String> out = new ArrayList<>();
        if (json.isJsonArray()) json.getAsJsonArray().forEach(e -> out.add(e.getAsString()));
        else if (json.isJsonPrimitive()) out.add(json.getAsString());
        return List.copyOf(out);
    }
}
