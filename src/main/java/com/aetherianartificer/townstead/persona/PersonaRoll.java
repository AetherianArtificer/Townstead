package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One thing a Persona rolls once per world, such as their hometown. Every option can pin the
 * Persona's profession and set Ink variables. The rolled value is itself an Ink variable named
 * after the roll.
 * <pre>
 * "rolls": {
 *   "hometown": [
 *     { "value": "mill", "profession": "townstead:baker", "vars": { "gift": "minecraft:bread" },
 *       "outfit": { "female": "townstead:skins/clothing/persona/builder_mill_f.png", "male": "..." } },
 *     { "value": "well", "weight": 1, "when": { "type": "pheno:config", ... }, "profession": "farmer" }
 *   ]
 * }
 * </pre>
 */
public record PersonaRoll(String name, List<Option> options) {

    public record Option(String value, int weight, @Nullable Condition when, @Nullable ResourceLocation profession,
                         Map<String, Object> vars, Map<String, String> outfit) {}

    /** Picks one option whose {@code when} holds for the player, by weight. Null when none can be picked. */
    public @Nullable Option pick(ServerPlayer player, RandomSource random) {
        List<Option> open = new ArrayList<>();
        int total = 0;
        for (Option option : options) {
            if (option.weight() <= 0) continue;
            if (option.when() != null && !option.when().test(new ConditionContext(player, null))) continue;
            open.add(option);
            total += option.weight();
        }
        if (open.isEmpty()) return null;
        int roll = random.nextInt(total);
        for (Option option : open) {
            roll -= option.weight();
            if (roll < 0) return option;
        }
        return open.get(open.size() - 1);
    }

    public @Nullable Option option(String value) {
        for (Option option : options) if (option.value().equals(value)) return option;
        return null;
    }

    static @Nullable PersonaRoll parse(String name, JsonElement json, List<String> issues) {
        if (!json.isJsonArray() || json.getAsJsonArray().isEmpty()) {
            issues.add("error: rolls." + name + ": needs a list of options");
            return null;
        }
        List<Option> options = new ArrayList<>();
        int index = 0;
        for (JsonElement element : json.getAsJsonArray()) {
            String where = "rolls." + name + "[" + index++ + "]";
            if (!element.isJsonObject() || !element.getAsJsonObject().has("value")) {
                issues.add("error: " + where + ": each option needs a \"value\"");
                continue;
            }
            JsonObject option = element.getAsJsonObject();
            Condition when = null;
            if (option.has("when")) {
                when = Conditions.parse(option.get("when"));
                if (when == null) issues.add("error: " + where + ".when: not a known Pheno condition");
            }
            ResourceLocation profession = null;
            if (option.has("profession")) {
                String raw = option.get("profession").getAsString();
                profession = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
                if (profession == null) issues.add("error: " + where + ".profession: '" + raw + "' is not an id");
            }
            Map<String, Object> vars = new LinkedHashMap<>();
            if (option.has("vars") && option.get("vars").isJsonObject()) {
                for (Map.Entry<String, JsonElement> var : option.getAsJsonObject("vars").entrySet()) {
                    Object value = inkValue(var.getValue());
                    if (value == null) issues.add("error: " + where + ".vars." + var.getKey() + ": must be text, a number or true/false");
                    else vars.put(var.getKey(), value);
                }
            }
            int weight = option.has("weight") ? option.get("weight").getAsInt() : 1;
            Map<String, String> outfit = option.has("outfit") ? Personas.outfit(option.get("outfit"), where + ".outfit", issues) : Map.of();
            options.add(new Option(option.get("value").getAsString(), weight, when, profession, Map.copyOf(vars), outfit));
        }
        return options.isEmpty() ? null : new PersonaRoll(name, List.copyOf(options));
    }

    private static @Nullable Object inkValue(JsonElement element) {
        if (!element.isJsonPrimitive()) return null;
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) return primitive.getAsBoolean();
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            return number == Math.rint(number) ? (Object) (int) number : (Object) (float) number;
        }
        return primitive.getAsString();
    }
}
