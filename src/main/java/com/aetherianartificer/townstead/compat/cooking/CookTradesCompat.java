package com.aetherianartificer.townstead.compat.cooking;

import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.ArrayList;
import java.util.List;

public final class CookTradesCompat {
    private static final float PRICE_MULTIPLIER = 0.05F;

    private CookTradesCompat() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!FarmersDelightCookAssignment.isExternalCookProfession(event.getType())) return;

        addTieredSellTrades(event, 1, 2, List.of(
                spec("farmersdelight:fried_egg", 2, 2, 16, 1),
                spec("farmersdelight:cabbage_rolls", 3, 1, 12, 1),
                spec("farmersdelight:salmon_roll", 3, 1, 12, 1),
                spec("farmersdelight:cod_roll", 3, 1, 12, 1)
        ), List.of(
                vanilla(Items.COOKED_CHICKEN, 2, 2, 16, 1),
                vanilla(Items.BREAD, 1, 4, 16, 1)
        ));

        addTieredSellTrades(event, 2, 2, List.of(
                spec("farmersdelight:beef_stew", 5, 1, 12, 5),
                spec("farmersdelight:chicken_soup", 5, 1, 12, 5),
                spec("farmersdelight:vegetable_soup", 4, 1, 12, 5),
                spec("farmersdelight:fish_stew", 5, 1, 12, 5)
        ), List.of(
                vanilla(Items.COOKED_BEEF, 2, 2, 16, 5),
                vanilla(Items.BAKED_POTATO, 1, 4, 16, 5)
        ));

        addTieredSellTrades(event, 3, 2, List.of(
                spec("farmersdelight:fried_rice", 6, 1, 10, 10),
                spec("farmersdelight:pumpkin_soup", 6, 1, 10, 10),
                spec("farmersdelight:noodle_soup", 6, 1, 10, 10),
                spec("farmersdelight:mushroom_rice", 6, 1, 10, 10)
        ), List.of(
                vanilla(Items.COOKED_PORKCHOP, 2, 2, 16, 10),
                vanilla(Items.COOKED_MUTTON, 2, 2, 16, 10)
        ));

        addTieredSellTrades(event, 4, 2, List.of(
                spec("farmersdelight:pasta_with_meatballs", 8, 1, 8, 15),
                spec("farmersdelight:pasta_with_mutton_chop", 8, 1, 8, 15),
                spec("farmersdelight:roasted_mutton_chops", 8, 1, 8, 15),
                spec("farmersdelight:steak_and_potatoes", 8, 1, 8, 15)
        ), List.of(
                vanilla(Items.RABBIT_STEW, 4, 1, 12, 15),
                vanilla(Items.PUMPKIN_PIE, 3, 2, 12, 15)
        ));

        addTieredSellTrades(event, 5, 2, List.of(
                spec("farmersdelight:stuffed_pumpkin", 12, 1, 6, 30),
                spec("farmersdelight:honey_glazed_ham", 12, 1, 6, 30),
                spec("farmersdelight:shepherds_pie", 12, 1, 6, 30),
                spec("farmersdelight:rice_roll_medley_block", 12, 1, 6, 30)
        ), List.of(
                vanilla(Items.GOLDEN_CARROT, 3, 3, 12, 30),
                vanilla(Items.SUSPICIOUS_STEW, 5, 1, 8, 30)
        ));
    }

    private static void addTieredSellTrades(
            VillagerTradesEvent event,
            int level,
            int maxOffers,
            List<TradeSpec> preferred,
            List<TradeSpec> fallback
    ) {
        List<VillagerTrades.ItemListing> levelTrades = event.getTrades().computeIfAbsent(level, k -> new ArrayList<>());

        List<VillagerTrades.ItemListing> resolved = new ArrayList<>();
        for (TradeSpec spec : preferred) {
            VillagerTrades.ItemListing listing = asSellListing(spec);
            if (listing != null) resolved.add(listing);
            if (resolved.size() >= maxOffers) break;
        }

        if (resolved.size() < maxOffers) {
            for (TradeSpec spec : fallback) {
                VillagerTrades.ItemListing listing = asSellListing(spec);
                if (listing != null) resolved.add(listing);
                if (resolved.size() >= maxOffers) break;
            }
        }

        levelTrades.addAll(resolved);
    }

    private static VillagerTrades.ItemListing asSellListing(TradeSpec spec) {
        Item item = resolveItem(spec.itemId());
        if (item == null) return null;
        return (trader, random) -> new MerchantOffer(
                new ItemCost(Items.EMERALD, spec.emeraldCost()),
                new ItemStack(item, spec.itemCount()),
                spec.maxUses(),
                spec.villagerXp(),
                PRICE_MULTIPLIER
        );
    }

    private static Item resolveItem(String itemId) {
        ResourceLocation key = ResourceLocation.tryParse(itemId);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return null;
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == Items.AIR ? null : item;
    }

    private static TradeSpec spec(String itemId, int emeraldCost, int itemCount, int maxUses, int villagerXp) {
        return new TradeSpec(itemId, emeraldCost, itemCount, maxUses, villagerXp);
    }

    private static TradeSpec vanilla(Item item, int emeraldCost, int itemCount, int maxUses, int villagerXp) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return new TradeSpec(key.toString(), emeraldCost, itemCount, maxUses, villagerXp);
    }

    private record TradeSpec(String itemId, int emeraldCost, int itemCount, int maxUses, int villagerXp) {}
}
