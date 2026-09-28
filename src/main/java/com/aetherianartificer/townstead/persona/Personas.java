package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.story.goal.Goal;
import com.aetherianartificer.townstead.story.goal.Goals;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loaded Personas. {@code persona.json} holds everything a {@code story.json} can (goals,
 * rewards, conditions, actions) plus who the Persona is and when they arrive:
 * <pre>
 * {
 *   "name": "Village Builder",
 *   "arrives": [
 *     { "text": "Raise buildings here", "value": { "type": "pheno:standing", "source": "deeds" }, "target": 9 },
 *     "good_standing"
 *   ],
 *   "arrival": "walk_in",
 *   "villager": { "profession": "minecraft:mason", "root": "townstead_roots:human", "gender": "female" },
 *   "downed": true,
 *   "requires_mods": ["farmersdelight"]
 * }
 * </pre>
 * Everything is optional. With no {@code arrives}, the Persona only comes through
 * {@code /townstead persona spawn}. Arrival goals are read on the player, where they stand.
 */
public final class Personas {
    private Personas() {}

    private static volatile Map<ResourceLocation, PersonaDefinition> all = Map.of();

    public static Map<ResourceLocation, PersonaDefinition> all() { return all; }

    public static @Nullable PersonaDefinition byId(ResourceLocation id) { return all.get(id); }

    public static void set(Map<ResourceLocation, PersonaDefinition> next) { all = Map.copyOf(next); }

    /** The story id a Persona's Ink compiles under. */
    public static ResourceLocation storyId(ResourceLocation persona) {
        return ResourceLocation.tryParse(persona.getNamespace() + ":persona/" + persona.getPath());
    }

    /**
     * Reads the Persona part of {@code persona.json}. Problems go into {@code issues} as
     * {@code error:} or {@code warning:} lines; null means the Persona is not loaded.
     */
    public static @Nullable PersonaDefinition parse(ResourceLocation id, JsonObject json, Map<String, JsonObject> goals,
                                                    List<String> issues) {
        for (String mod : strings(json.get("requires_mods"))) {
            if (!ModCompat.isLoaded(mod)) {
                issues.add("warning: not loaded, because mod '" + mod + "' is not installed");
                return null;
            }
        }
        String name = json.has("name") ? json.get("name").getAsString() : id.getPath();
        List<Goal> arrives = new ArrayList<>();
        JsonElement arrivesJson = json.get("arrives");
        List<JsonElement> entries = new ArrayList<>();
        if (arrivesJson != null && arrivesJson.isJsonArray()) arrivesJson.getAsJsonArray().forEach(entries::add);
        else if (arrivesJson != null) entries.add(arrivesJson);
        for (JsonElement entry : entries) {
            Goals.Parsed parsed = entry.isJsonObject()
                    ? Goals.inline(entry.getAsJsonObject(), "player")
                    : Goals.resolve(entry.getAsString(), id.getNamespace(), goals, "player");
            if (parsed.goal() == null) {
                issues.add("error: arrives: " + parsed.error());
            } else if (parsed.goal().isCounter()) {
                issues.add("error: arrives: arrival goals must be a condition or a value, not an event count");
            } else {
                arrives.add(parsed.goal());
            }
        }
        PersonaDefinition.Arrival arrival = PersonaDefinition.Arrival.WALK_IN;
        if (json.has("arrival")) {
            try {
                arrival = PersonaDefinition.Arrival.valueOf(json.get("arrival").getAsString().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                issues.add("error: arrival must be \"walk_in\" or \"appear\"");
            }
        }
        ResourceLocation profession = null, root = null;
        String gender = null;
        if (json.has("villager") && json.get("villager").isJsonObject()) {
            JsonObject villager = json.getAsJsonObject("villager");
            if (villager.has("profession")) {
                String raw = villager.get("profession").getAsString();
                profession = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
                if (profession == null) issues.add("error: villager.profession: '" + raw + "' is not an id");
            }
            if (villager.has("root")) {
                root = ResourceLocation.tryParse(villager.get("root").getAsString());
                if (root == null) issues.add("error: villager.root: not an id");
            }
            if (villager.has("gender")) {
                gender = villager.get("gender").getAsString().toLowerCase(Locale.ROOT);
                if (!gender.equals("male") && !gender.equals("female")) {
                    issues.add("error: villager.gender must be \"male\" or \"female\"");
                    gender = null;
                }
            }
        }
        boolean downed = !json.has("downed") || json.get("downed").getAsBoolean();
        if (arrives.isEmpty() && arrivesJson == null) {
            issues.add("warning: no \"arrives\"; this Persona only comes through /townstead persona spawn");
        }
        if (issues.stream().anyMatch(i -> i.startsWith("error:"))) return null;
        return new PersonaDefinition(id, storyId(id), name, List.copyOf(arrives), arrival, profession, root, gender, downed);
    }

    private static List<String> strings(@Nullable JsonElement element) {
        List<String> out = new ArrayList<>();
        if (element == null) return out;
        if (element.isJsonArray()) {
            for (JsonElement e : (JsonArray) element) out.add(e.getAsString());
        } else {
            out.add(element.getAsString());
        }
        return out;
    }
}
