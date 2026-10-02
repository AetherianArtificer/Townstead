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
 *   "villager": { "profession": "minecraft:mason", "root": "townstead_roots:human", "gender": "female",
 *                 "schedule": "townstead:long_days", "personality": ["friendly", "witty"] },
 *   "downed": true,
 *   "unique": "village",
 *   "rolls": { "hometown": [ { "value": "mill", "profession": "townstead:baker", "vars": { "gift": "minecraft:bread" } } ] },
 *   "gifts": [ { "items": ["$gift"], "response": "loves", "knot": "gift_loved" } ],
 *   "requires_mods": ["farmersdelight"]
 * }
 * </pre>
 * {@code unique} is {@code village} (default: one per village, so each player's settlement can
 * have its own) or {@code world} (only one in the whole world). Everything is optional. With no {@code arrives}, the Persona only comes through
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
        ResourceLocation profession = null, root = null, schedule = null;
        Map<String, String> outfit = Map.of();
        Map<String, Integer> personalities = new java.util.LinkedHashMap<>();
        String gender = null;
        Map<ResourceLocation, Double> states = new java.util.LinkedHashMap<>();
        ResourceLocation mainhand = null;
        if (json.has("villager") && json.get("villager").isJsonObject()) {
            JsonObject villager = json.getAsJsonObject("villager");
            if (villager.has("profession")) {
                String raw = villager.get("profession").getAsString();
                profession = ResourceLocation.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
                if (profession == null) issues.add("error: villager.profession: '" + raw + "' is not an id");
            }
            if (villager.has("personality")) {
                JsonElement raw = villager.get("personality");
                if (raw.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : raw.getAsJsonObject().entrySet()) personalities.put(e.getKey(), e.getValue().getAsInt());
                } else {
                    for (String ref : strings(raw)) personalities.put(ref, 1);
                }
                if (personalities.isEmpty()) issues.add("error: villager.personality: name at least one personality");
            }
            if (villager.has("outfit")) outfit = outfit(villager.get("outfit"), "villager.outfit", issues);
            if (villager.has("schedule")) {
                schedule = ResourceLocation.tryParse(villager.get("schedule").getAsString());
                if (schedule == null) issues.add("error: villager.schedule: not an id");
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
            if (villager.has("states") && villager.get("states").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : villager.getAsJsonObject("states").entrySet()) {
                    ResourceLocation state = ResourceLocation.tryParse(e.getKey());
                    if (state == null) issues.add("error: villager.states: '" + e.getKey() + "' is not an id");
                    else states.put(state, e.getValue().getAsDouble());
                }
            }
            if (villager.has("mainhand")) {
                mainhand = ResourceLocation.tryParse(villager.get("mainhand").getAsString());
                if (mainhand == null) issues.add("error: villager.mainhand: not an id");
            }
        }
        boolean downed = !json.has("downed") || json.get("downed").getAsBoolean();
        boolean worldUnique = false;
        if (json.has("unique")) {
            String unique = json.get("unique").getAsString().toLowerCase(Locale.ROOT);
            if (unique.equals("world")) worldUnique = true;
            else if (!unique.equals("village")) issues.add("error: unique must be \"village\" or \"world\"");
        }
        List<PersonaRoll> rolls = new ArrayList<>();
        if (json.has("rolls")) {
            if (!json.get("rolls").isJsonObject()) {
                issues.add("error: rolls must be an object of named lists");
            } else {
                for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("rolls").entrySet()) {
                    PersonaRoll roll = PersonaRoll.parse(entry.getKey(), entry.getValue(), issues);
                    if (roll != null) rolls.add(roll);
                }
            }
        }
        List<PersonaGift> gifts = new ArrayList<>();
        if (json.has("gifts")) {
            if (!json.get("gifts").isJsonArray()) {
                issues.add("error: gifts must be a list");
            } else {
                int index = 0;
                for (JsonElement entry : json.getAsJsonArray("gifts")) {
                    PersonaGift gift = PersonaGift.parse(entry, index++, issues);
                    if (gift != null) gifts.add(gift);
                }
            }
        }
        PersonaNames names = json.has("names") ? PersonaNames.parse(json.get("names"), issues) : null;
        java.util.Set<String> movesOn = new java.util.LinkedHashSet<>();
        for (String trigger : strings(json.get("moves_on"))) {
            if (!trigger.equals(PersonaMoves.ORDER_DISSOLVED) && !trigger.equals(PersonaMoves.TOWN_FALLEN)) {
                issues.add("error: moves_on: '" + trigger + "' is not \"" + PersonaMoves.ORDER_DISSOLVED
                        + "\" or \"" + PersonaMoves.TOWN_FALLEN + "\"");
            } else {
                movesOn.add(trigger);
            }
        }
        ResourceLocation dialogueTheme = null;
        JsonObject dialogueThemeJson = null;
        if (json.has("dialogue_theme")) {
            if (json.get("dialogue_theme").isJsonObject()) {
                dialogueThemeJson = json.getAsJsonObject("dialogue_theme");
                dialogueTheme = com.aetherianartificer.townstead.dialogue.DialogueThemeData.inlineId(id);
            } else {
                dialogueTheme = ResourceLocation.tryParse(json.get("dialogue_theme").getAsString());
                if (dialogueTheme == null) issues.add("error: dialogue_theme: not an id or a theme object");
            }
        }
        java.util.Set<String> hiddenMenu = new java.util.LinkedHashSet<>(PersonaDefinition.SMALL_TALK);
        if (json.has("mca_menu")) {
            JsonElement menu = json.get("mca_menu");
            if (menu.isJsonPrimitive() && menu.getAsString().equals("all")) {
                hiddenMenu.clear();
            } else if (menu.isJsonObject()) {
                hiddenMenu.removeAll(strings(menu.getAsJsonObject().get("keep")));
                hiddenMenu.addAll(strings(menu.getAsJsonObject().get("hide")));
            } else {
                issues.add("error: mca_menu must be \"all\" or an object with \"keep\" and/or \"hide\" lists");
            }
        }
        boolean familyToldOnly = false;
        if (json.has("family_name")) {
            String mode = json.get("family_name").getAsString();
            if (mode.equals("told")) familyToldOnly = true;
            else if (!mode.equals("shown")) issues.add("error: family_name must be \"shown\" or \"told\"");
        }
        if (arrives.isEmpty() && arrivesJson == null) {
            issues.add("warning: no \"arrives\"; this Persona only comes through /townstead persona spawn");
        }
        if (issues.stream().anyMatch(i -> i.startsWith("error:"))) return null;
        return new PersonaDefinition(id, storyId(id), name, List.copyOf(arrives), arrival, profession, root, gender, downed, List.copyOf(rolls), List.copyOf(gifts), schedule, Map.copyOf(personalities), worldUnique, outfit,
                names, Map.copyOf(states), mainhand, java.util.Set.copyOf(movesOn), dialogueTheme, dialogueThemeJson, java.util.Set.copyOf(hiddenMenu), familyToldOnly);
    }

    /**
     * An outfit: one MCA clothing texture id for everyone, or an object keyed {@code female},
     * {@code male} and {@code any}. The texture must be listed in an MCA clothing file.
     */
    static Map<String, String> outfit(JsonElement json, String where, List<String> issues) {
        Map<String, String> out = new java.util.LinkedHashMap<>();
        if (json.isJsonPrimitive()) {
            out.put("any", json.getAsString());
        } else if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                String key = e.getKey().toLowerCase(Locale.ROOT);
                if (!key.equals("female") && !key.equals("male") && !key.equals("any")) {
                    issues.add("error: " + where + ": use \"female\", \"male\" or \"any\", not '" + e.getKey() + "'");
                    continue;
                }
                out.put(key, e.getValue().getAsString());
            }
        } else {
            issues.add("error: " + where + ": a texture id, or an object by gender");
        }
        return Map.copyOf(out);
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
