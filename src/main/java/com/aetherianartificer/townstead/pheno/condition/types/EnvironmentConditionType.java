package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One consolidated environment query that folds several common leaf conditions into a single,
 * readable block. Each category is optional; when present, all categories must hold (AND), and
 * a list within a category passes if any entry matches (OR):
 *
 * <pre>
 * { "type": "pheno:environment",
 *   "weather": ["rain", "thunder"],   // rain | thunder | snow | clear
 *   "exposure": "sky",                 // sky | sun
 *   "time": "night",                   // day | night
 *   "biome": "#minecraft:is_cold",
 *   "dimension": "minecraft:the_nether",
 *   "effects": { "any": "#c:harmful", "count": { "min": 1 } } }
 * </pre>
 *
 * <p>It composes the existing leaf condition types by parsing canonical sub-condition JSON, so
 * there is no duplicated state logic and the result behaves exactly like hand-written
 * {@code and}/{@code or} of those leaves.
 */
public final class EnvironmentConditionType implements ConditionType {

    public static final String KEY = "pheno:environment";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    @Nullable
    public Condition parse(JsonObject json) {
        List<Condition> all = new ArrayList<>();
        if (json.has("weather")) {
            Condition c = anyOf(scalarOrList(json.get("weather")), EnvironmentConditionType::weatherToken);
            if (c == null) return null;
            all.add(c);
        }
        if (json.has("exposure")) {
            Condition c = anyOf(scalarOrList(json.get("exposure")), EnvironmentConditionType::exposureToken);
            if (c == null) return null;
            all.add(c);
        }
        if (json.has("time")) {
            Condition c = anyOf(scalarOrList(json.get("time")), EnvironmentConditionType::timeToken);
            if (c == null) return null;
            all.add(c);
        }
        if (json.has("biome")) {
            Condition c = anyOf(scalarOrList(json.get("biome")), v -> leaf("biome", "biome", v));
            if (c == null) return null;
            all.add(c);
        }
        if (json.has("dimension")) {
            Condition c = anyOf(scalarOrList(json.get("dimension")), v -> leaf("dimension", "dimension", v));
            if (c == null) return null;
            all.add(c);
        }
        if (json.has("effects") && json.get("effects").isJsonObject()) {
            Condition c = effects(json.getAsJsonObject("effects"));
            if (c == null) return null;
            all.add(c);
        }
        if (all.isEmpty()) return null;
        List<Condition> fixed = List.copyOf(all);
        return ctx -> {
            for (Condition c : fixed) {
                if (!c.test(ctx)) return false;
            }
            return true;
        };
    }

    @Nullable
    private static Condition weatherToken(String token) {
        return switch (token) {
            case "rain", "raining" -> stateLeaf("raining");
            case "thunder", "thunderstorm", "thundering" -> stateLeaf("thundering");
            case "snow", "snowing" -> stateLeaf("in_snow");
            case "clear", "sunny" -> negate(stateLeaf("raining"));
            default -> null;
        };
    }

    @Nullable
    private static Condition exposureToken(String token) {
        return switch (token) {
            case "sky" -> stateLeaf("exposed_to_sky");
            case "sun" -> stateLeaf("exposed_to_sun");
            default -> null;
        };
    }

    @Nullable
    private static Condition timeToken(String token) {
        return switch (token) {
            case "day" -> stateLeaf("daytime");
            case "night" -> negate(stateLeaf("daytime"));
            default -> null;
        };
    }

    @Nullable
    private static Condition effects(JsonObject effects) {
        if (!effects.has("any")) return null;
        String id = GsonHelper.getAsString(effects, "any", "");
        if (id.isEmpty()) return null;
        JsonObject o = new JsonObject();
        if (id.startsWith("#")) {
            o.addProperty("type", "pheno:status_effect_tag");
            o.addProperty("tag", id.substring(1));
            if (effects.has("count") && effects.get("count").isJsonObject()) {
                JsonObject count = effects.getAsJsonObject("count");
                if (count.has("min")) o.addProperty("min_count", GsonHelper.getAsInt(count, "min", 1));
            }
        } else {
            o.addProperty("type", "pheno:status_effect");
            o.addProperty("effect", id);
        }
        return Conditions.parse(o);
    }

    /** OR of the conditions each token maps to; null if any token does not map (a real error). */
    @Nullable
    private static Condition anyOf(List<String> tokens, Function<String, Condition> map) {
        List<Condition> conditions = new ArrayList<>();
        for (String token : tokens) {
            Condition c = map.apply(token);
            if (c == null) return null;
            conditions.add(c);
        }
        if (conditions.isEmpty()) return null;
        if (conditions.size() == 1) return conditions.get(0);
        List<Condition> fixed = List.copyOf(conditions);
        return ctx -> {
            for (Condition c : fixed) {
                if (c.test(ctx)) return true;
            }
            return false;
        };
    }

    @Nullable
    private static Condition stateLeaf(String name) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "pheno:" + name);
        return Conditions.parse(o);
    }

    @Nullable
    private static Condition leaf(String type, String field, String value) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "pheno:" + type);
        o.addProperty(field, value);
        return Conditions.parse(o);
    }

    @Nullable
    private static Condition negate(@Nullable Condition condition) {
        return condition == null ? null : condition.negate();
    }

    private static List<String> scalarOrList(JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive()) out.add(e.getAsString());
            }
        } else if (element.isJsonPrimitive()) {
            out.add(element.getAsString());
        }
        return out;
    }
}
