package com.aetherianartificer.townstead.origin.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * One rolled drop in a life stage's {@code death_loot}: an {@code item}, a {@code [min,max]} count, and a
 * {@code chance} (0..1) to drop at all. Authored inline on a life-stage in the {@code life_cycle} gene
 * (e.g. an egg stage drops a {@code minecraft:egg}); rolled + spawned server-side by {@link DeathLoot}
 * when a villager or player of that origin dies, on top of normal drops.
 */
public record LootDrop(ResourceLocation item, int min, int max, float chance) {

    /** Parse a {@code death_loot} array; entries with {@code count} pin min==max. Bad entries are skipped. */
    public static List<LootDrop> parseList(JsonArray arr) {
        List<LootDrop> out = new ArrayList<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String item = GsonHelper.getAsString(o, "item", "");
            ResourceLocation id = item.isEmpty() ? null : ResourceLocation.tryParse(item);
            if (id == null) continue;
            int min;
            int max;
            if (o.has("count")) {
                min = max = GsonHelper.getAsInt(o, "count", 1);
            } else {
                min = GsonHelper.getAsInt(o, "min", 1);
                max = GsonHelper.getAsInt(o, "max", min);
            }
            if (max < min) max = min;
            float chance = GsonHelper.getAsFloat(o, "chance", 1.0f);
            out.add(new LootDrop(id, Math.max(0, min), Math.max(0, max), Math.max(0f, Math.min(1f, chance))));
        }
        return out;
    }

    /** The count to drop this roll (0 = nothing, after the chance check). */
    public int rollCount(RandomSource rng) {
        if (chance < 1f && rng.nextFloat() >= chance) return 0;
        return min + (max > min ? rng.nextInt(max - min + 1) : 0);
    }
}
