package com.aetherianartificer.townstead.gift;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One way a villager takes a gift, from {@code data/<ns>/gift_reaction/<name>.json}. When a player
 * gives a villager an item in {@code items} and {@code condition} holds (the villager is the
 * entity, the player is {@code other}), Townstead takes the gift this way instead of MCA's usual way.
 * <pre>
 * { "schema": "townstead:gift_reaction/v1",
 *   "priority": 0,
 *   "items": ["examplemod:garlic_bread", "#examplepack:holy_things"],
 *   "condition": { ... },
 *   "response": "dislikes",            // loves | likes | dislikes | neutral
 *   "satisfaction": -60,
 *   "relationship": { "townstead:affection": -10 },
 *   "item": "return",                  // keep | use | return
 *   "action": { ... },
 *   "knot": "gift_hated", "line": "gift.fail",
 *   "first": { "knot": "...", "line": "...", "relationship": { ... }, "per_item": false } }
 * </pre>
 */
public record GiftReaction(ResourceLocation id, int priority, List<String> items, @Nullable Condition condition,
                           GiftEngine.Response response, GiftEngine.Fate fate, int satisfaction,
                           Map<String, Float> relationship, @Nullable Action action,
                           @Nullable String knot, @Nullable String line, @Nullable First first) {

    public static final String SCHEMA = "townstead:gift_reaction/v1";

    /** What happens instead the first time a player gives this villager this kind of gift. */
    public record First(@Nullable String knot, @Nullable String line, Map<String, Float> relationship, boolean perItem) {}

    public static GiftReaction parse(ResourceLocation id, JsonObject json) {
        List<String> items = new ArrayList<>();
        JsonElement raw = json.get("items");
        if (raw != null && raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> items.add(e.getAsString()));
        else if (raw != null) items.add(raw.getAsString());
        if (items.isEmpty()) throw new IllegalArgumentException("needs \"items\"");
        GiftEngine.Response response = enumValue(GiftEngine.Response.class,
                GsonHelper.getAsString(json, "response", "likes"), "response");
        GiftEngine.Fate fate = json.has("item")
                ? enumValue(GiftEngine.Fate.class, GsonHelper.getAsString(json, "item"), "item")
                : GiftEngine.defaultFate(response);
        Condition condition = null;
        if (json.has("condition")) {
            condition = Conditions.parse(json.get("condition"));
            if (condition == null) throw new IllegalArgumentException("unreadable \"condition\"");
        }
        Action action = null;
        if (json.has("action")) {
            action = Actions.parse(json.get("action"));
            if (action == null) throw new IllegalArgumentException("unreadable \"action\"");
        }
        First first = null;
        if (json.has("first")) {
            JsonObject f = GsonHelper.getAsJsonObject(json, "first");
            first = new First(optional(f, "knot"), optional(f, "line"), relationship(f), GsonHelper.getAsBoolean(f, "per_item", false));
        }
        return new GiftReaction(id, GsonHelper.getAsInt(json, "priority", 0), List.copyOf(items), condition,
                response, fate, GsonHelper.getAsInt(json, "satisfaction", GiftEngine.defaultSatisfaction(response)),
                relationship(json), action, optional(json, "knot"), optional(json, "line"), first);
    }

    private static @Nullable String optional(JsonObject json, String key) {
        return json.has(key) ? GsonHelper.getAsString(json, key) : null;
    }

    private static Map<String, Float> relationship(JsonObject json) {
        Map<String, Float> out = new LinkedHashMap<>();
        if (!json.has("relationship")) return Map.of();
        for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(json, "relationship").entrySet()) {
            out.put(e.getKey(), e.getValue().getAsFloat());
        }
        return Map.copyOf(out);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) {
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown \"" + field + "\": " + raw);
        }
    }
}
