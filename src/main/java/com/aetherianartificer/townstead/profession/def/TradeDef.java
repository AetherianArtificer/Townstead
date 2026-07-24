package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * One data-defined trade offer in a profession def's {@code trades} block, keyed by merchant
 * level:
 *
 * <pre>
 * "trades": { "1": [ { "cost": { "item": "minecraft:emerald", "count": 3 },
 *                      "result": { "item": "minecraft:bread", "count": 6 },
 *                      "max_uses": 12, "villager_xp": 2, "price_multiplier": 0.05 } ] }
 * </pre>
 *
 * An optional {@code secondary_cost} adds the second input slot. Items are resolved at offer
 * time, so a trade referencing an absent mod's item simply never appears. An optional
 * {@code requires_skill} (bare refs scope to the owning profession's directory) hides the
 * offer until the merchant has learned that skill, so a specialization path's wares only
 * appear once the villager specs into the path.
 */
public record TradeDef(
        ResourceLocation costItem, int costCount,
        @Nullable ResourceLocation secondaryCostItem, int secondaryCostCount,
        ResourceLocation resultItem, int resultCount,
        int maxUses, int villagerXp, float priceMultiplier,
        @Nullable ResourceLocation requiresSkill) {

    @Nullable
    public static TradeDef parse(JsonObject json) {
        return parse(json, null);
    }

    @Nullable
    public static TradeDef parse(JsonObject json, @Nullable ResourceLocation profession) {
        ResourceLocation cost = itemId(json, "cost");
        ResourceLocation result = itemId(json, "result");
        if (cost == null || result == null) return null;
        ResourceLocation secondary = itemId(json, "secondary_cost");
        return new TradeDef(
                cost, itemCount(json, "cost"),
                secondary, secondary == null ? 0 : itemCount(json, "secondary_cost"),
                result, itemCount(json, "result"),
                GsonHelper.getAsInt(json, "max_uses", 12),
                GsonHelper.getAsInt(json, "villager_xp", 2),
                GsonHelper.getAsFloat(json, "price_multiplier", 0.05f),
                skillRef(GsonHelper.getAsString(json, "requires_skill", ""), profession));
    }

    @Nullable
    private static ResourceLocation skillRef(String raw, @Nullable ResourceLocation profession) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.contains(":")) return ResourceLocation.tryParse(raw);
        if (profession == null) return null;
        return ResourceLocation.tryParse(
                profession.getNamespace() + ":" + profession.getPath() + "/" + raw);
    }

    @Nullable
    private static ResourceLocation itemId(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonObject()) return null;
        return ResourceLocation.tryParse(GsonHelper.getAsString(json.getAsJsonObject(key), "item", ""));
    }

    private static int itemCount(JsonObject json, String key) {
        return Math.max(1, GsonHelper.getAsInt(json.getAsJsonObject(key), "count", 1));
    }
}
