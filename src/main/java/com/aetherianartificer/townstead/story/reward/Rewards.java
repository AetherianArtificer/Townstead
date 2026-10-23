package com.aetherianartificer.townstead.story.reward;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.story.goal.Goals;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link Reward}s from data, the same way goals are built. A reward is a JSON object,
 * written inline in {@code story.json} under {@code rewards} or shared in
 * {@code data/<ns>/reward/<id>.json}, and may take {@code params}:
 * <pre>
 * { "items": [ { "item": "minecraft:wheat_seeds", "count": 8 } ] }
 *
 * { "text": "A sack of whatever {teller} dug up", "loot_table": "minecraft:chests/village/village_plains_house" }
 *
 * { "params": ["count"], "text": "{count} bread, still warm", "action": { "type": "pheno:give", "item": "minecraft:bread", "count": "$count" } }
 * </pre>
 * A quest names rewards in Ink with {@code # reward: seed_bag} or {@code # reward: bread(6)}.
 * Item rewards need no text; the ledger shows the items.
 */
public final class Rewards {
    private Rewards() {}

    private static volatile Map<ResourceLocation, JsonObject> library = Map.of();

    public static void setLibrary(Map<ResourceLocation, JsonObject> next) {
        library = Map.copyOf(next);
    }

    public static Map<ResourceLocation, JsonObject> library() {
        return library;
    }

    public record Parsed(@Nullable Reward reward, @Nullable String error) {
        static Parsed fail(String error) { return new Parsed(null, error); }
    }

    public static Parsed resolve(String raw, String namespace, Map<String, JsonObject> inline) {
        Goals.Reference ref = Goals.reference(raw);
        if (ref == null) return Parsed.fail("'" + raw + "' is not a reward name");
        JsonObject template = Goals.find(ref.name(), namespace, inline, library);
        if (template == null) return Parsed.fail("no reward named '" + ref.name() + "' in story.json or data/*/reward/");
        return build(template, ref.args());
    }

    static Parsed build(JsonObject template, List<String> args) {
        Goals.Instance instance;
        try {
            instance = Goals.instantiate(template, args);
        } catch (IllegalArgumentException e) {
            return Parsed.fail(e.getMessage());
        }
        JsonObject json = instance.json();
        String text = json.has("text") ? Goals.fillParams(json.get("text").getAsString(), instance.values()) : null;
        List<Reward.Stack> items = new ArrayList<>();
        try {
            if (json.has("items")) {
                for (JsonElement element : json.getAsJsonArray("items")) {
                    JsonObject entry = element.getAsJsonObject();
                    ResourceLocation id = ResourceLocation.tryParse(entry.get("item").getAsString());
                    if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                        return Parsed.fail("no item '" + entry.get("item").getAsString() + "'");
                    }
                    int count = entry.has("count") ? entry.get("count").getAsInt() : 1;
                    if (count < 1) return Parsed.fail("item counts start at 1");
                    items.add(new Reward.Stack(id, count));
                }
            }
            ResourceLocation lootTable = null;
            if (json.has("loot_table")) {
                lootTable = ResourceLocation.tryParse(json.get("loot_table").getAsString());
                if (lootTable == null) return Parsed.fail("'" + json.get("loot_table").getAsString() + "' is not a loot table id");
            }
            Action action = null;
            if (json.has("action")) {
                action = Actions.parse(json.get("action"));
                if (action == null) return Parsed.fail("action is not a known Pheno action");
            }
            if (items.isEmpty() && lootTable == null && action == null) {
                return Parsed.fail("a reward needs \"items\", \"loot_table\" or \"action\"");
            }
            if (text == null && (lootTable != null || action != null)) {
                return Parsed.fail("a loot table or action reward needs \"text\", the line shown in the Quest Ledger");
            }
            return new Parsed(new Reward(text, items, lootTable, action), null);
        } catch (RuntimeException e) {
            return Parsed.fail(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }
}
