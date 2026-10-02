package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A mob whose spawns become people: a villager of a regional Root, arriving with {@code states}
 * (a random amount in each range), who acts on {@code behavior} and stands in for the mob where it
 * counts, crediting its kills and dropping its loot. While its states last it belongs to the
 * disposition {@code group}; enough hearts from a player ({@code settle_hearts}) settle it into a
 * village. At most {@code cap} of them roam within {@code cap_radius} blocks, standing in for
 * the mob cap they would otherwise escape; a spawn past it is cancelled. {@code outfit} dresses
 * them from a pool per slot ({@code head}, {@code chest}, {@code legs}, {@code feet}), never
 * dropped; {@code "minecraft:air"} in a pool leaves the slot bare. {@code setting} names a server setting that switches the replacement off; none means
 * always on.
 */
public record MobReplacement(ResourceLocation id, Set<ResourceLocation> types, Set<ResourceLocation> tags,
                             float chance, Set<String> spawnTypes, Map<ResourceLocation, double[]> states,
                             @Nullable ResourceLocation behavior, @Nullable String group,
                             int settleHearts, int cap, double capRadius,
                             Map<String, List<ResourceLocation>> outfit, @Nullable String setting,
                             boolean creditKills, boolean replacedLoot) {
    public static final String SCHEMA = "townstead:mob_replacement/v1";
    private static final Set<String> DEFAULT_SPAWN_TYPES = Set.of("natural", "spawner");
    private static final int DEFAULT_SETTLE_HEARTS = 25;
    private static final int DEFAULT_CAP = 6;
    private static final double DEFAULT_CAP_RADIUS = 96;

    public MobReplacement {
        types = Set.copyOf(types);
        tags = Set.copyOf(tags);
        spawnTypes = Set.copyOf(spawnTypes);
        states = Map.copyOf(states);
        outfit = Map.copyOf(outfit);
    }

    public static final List<String> OUTFIT_SLOTS = List.of("head", "chest", "legs", "feet");

    static MobReplacement parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        Set<ResourceLocation> types = new LinkedHashSet<>();
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        if (!json.has("replaces") || !json.get("replaces").isJsonArray()) {
            throw new IllegalArgumentException("'replaces' must be an array of entity ids or #tags");
        }
        for (JsonElement element : json.getAsJsonArray("replaces")) {
            String raw = element.getAsString().trim();
            ResourceLocation parsed = DataPackLang.parseId(raw.startsWith("#") ? raw.substring(1) : raw);
            if (parsed == null) throw new IllegalArgumentException("'" + raw + "' is not an entity id");
            (raw.startsWith("#") ? tags : types).add(parsed);
        }
        if (types.isEmpty() && tags.isEmpty()) throw new IllegalArgumentException("'replaces' is empty");

        float chance = GsonHelper.getAsFloat(json, "chance", 1f);
        if (!(chance >= 0 && chance <= 1)) throw new IllegalArgumentException("'chance' must be in [0,1]");

        Set<String> spawnTypes = new LinkedHashSet<>();
        if (json.has("spawn_types")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "spawn_types")) {
                spawnTypes.add(element.getAsString().toLowerCase(Locale.ROOT));
            }
        } else {
            spawnTypes.addAll(DEFAULT_SPAWN_TYPES);
        }

        Map<ResourceLocation, double[]> states = new LinkedHashMap<>();
        if (json.has("states")) {
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "states").entrySet()) {
                ResourceLocation state = DataPackLang.parseId(entry.getKey());
                if (state == null) throw new IllegalArgumentException("'" + entry.getKey() + "' is not a state id");
                JsonElement value = entry.getValue();
                double[] range = value.isJsonArray()
                        ? new double[]{value.getAsJsonArray().get(0).getAsDouble(), value.getAsJsonArray().get(1).getAsDouble()}
                        : new double[]{value.getAsDouble(), value.getAsDouble()};
                if (range[1] < range[0]) throw new IllegalArgumentException("state range for " + state + " is reversed");
                states.put(state, range);
            }
        }

        ResourceLocation behavior = json.has("behavior") ? DataPackLang.parseId(GsonHelper.getAsString(json, "behavior")) : null;
        String group = json.has("group") ? GsonHelper.getAsString(json, "group") : null;
        int settleHearts = GsonHelper.getAsInt(json, "settle_hearts", DEFAULT_SETTLE_HEARTS);
        int cap = GsonHelper.getAsInt(json, "cap", DEFAULT_CAP);
        if (cap < 0) throw new IllegalArgumentException("'cap' must not be negative");
        double capRadius = GsonHelper.getAsDouble(json, "cap_radius", DEFAULT_CAP_RADIUS);
        Map<String, List<ResourceLocation>> outfit = new LinkedHashMap<>();
        if (json.has("outfit")) {
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "outfit").entrySet()) {
                if (!OUTFIT_SLOTS.contains(entry.getKey())) {
                    throw new IllegalArgumentException("outfit slot '" + entry.getKey() + "' must be one of " + OUTFIT_SLOTS);
                }
                List<ResourceLocation> pool = new java.util.ArrayList<>();
                for (JsonElement item : entry.getValue().getAsJsonArray()) {
                    ResourceLocation itemId = DataPackLang.parseId(item.getAsString());
                    if (itemId == null) throw new IllegalArgumentException("'" + item.getAsString() + "' is not an item id");
                    pool.add(itemId);
                }
                if (!pool.isEmpty()) outfit.put(entry.getKey(), List.copyOf(pool));
            }
        }
        String setting = json.has("setting") ? GsonHelper.getAsString(json, "setting") : null;
        String loot = GsonHelper.getAsString(json, "loot", "replaced");
        if (!loot.equals("replaced") && !loot.equals("own")) throw new IllegalArgumentException("'loot' must be \"replaced\" or \"own\"");
        return new MobReplacement(id, types, tags, chance, spawnTypes, states, behavior, group, settleHearts, cap, capRadius, outfit, setting,
                GsonHelper.getAsBoolean(json, "credit_kills", true), loot.equals("replaced"));
    }
}
