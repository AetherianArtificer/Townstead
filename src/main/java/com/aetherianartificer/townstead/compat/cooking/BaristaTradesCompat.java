package com.aetherianartificer.townstead.compat.cooking;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightBaristaAssignment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.21 {
import net.minecraft.world.item.trading.ItemCost;
//?}
import net.minecraft.world.item.trading.MerchantOffer;
//? if neoforge {
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
//?} else if forge {
/*import net.minecraftforge.event.village.VillagerTradesEvent;
*///?}

import java.util.ArrayList;
import java.util.List;

public final class BaristaTradesCompat {
    private static final float PRICE_MULTIPLIER = 0.05F;

    private BaristaTradesCompat() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!ModCompat.isLoaded("rusticdelight")) return;
        if (!FarmersDelightBaristaAssignment.isBaristaProfession(event.getType())) return;

        addTieredTrades(event, 1, 2, List.of(
                buy("rusticdelight:coffee_beans", 16, 1, 16, 1),
                sell("rusticdelight:coffee", 2, 1, 12, 1),
                sell("rusticdelight:roasted_coffee_beans", 1, 2, 16, 1)
        ), List.of(
                sell("minecraft:cookie", 1, 6, 16, 1)
        ));

        addTieredTrades(event, 2, 2, List.of(
                buy("minecraft:sugar", 14, 1, 16, 5),
                sell("rusticdelight:milk_coffee", 3, 1, 12, 5),
                sell("rusticdelight:honey_coffee", 3, 1, 12, 5)
        ), List.of(
                sell("minecraft:bread", 1, 4, 16, 5)
        ));

        addTieredTrades(event, 3, 2, List.of(
                buy("minecraft:cocoa_beans", 8, 1, 16, 10),
                sell("rusticdelight:syrup_coffee", 4, 1, 10, 10),
                sell("rusticdelight:dark_coffee", 4, 1, 10, 10)
        ), List.of(
                sell("minecraft:pumpkin_pie", 2, 2, 12, 10)
        ));

        addTieredTrades(event, 4, 2, List.of(
                buy("minecraft:honey_bottle", 3, 1, 12, 15),
                sell("rusticdelight:chocolate_coffee", 6, 1, 8, 15)
        ), List.of(
                sell("minecraft:golden_carrot", 3, 2, 12, 15)
        ));

        addTieredTrades(event, 5, 2, List.of(
                buy("rusticdelight:roasted_coffee_beans", 6, 1, 12, 30),
                sell("rusticdelight:chocolate_coffee", 5, 2, 6, 30)
        ), List.of(
                sell("minecraft:cake", 8, 1, 6, 30)
        ));
    }

    private static void addTieredTrades(
            VillagerTradesEvent event,
            int level,
            int maxOffers,
            List<TradeSpec> preferred,
            List<TradeSpec> fallback
    ) {
        List<VillagerTrades.ItemListing> levelTrades = event.getTrades().computeIfAbsent(level, k -> new ArrayList<>());

        List<VillagerTrades.ItemListing> resolved = new ArrayList<>();
        for (TradeSpec spec : preferred) {
            VillagerTrades.ItemListing listing = asListing(spec);
            if (listing != null) resolved.add(listing);
            if (resolved.size() >= maxOffers) break;
        }

        if (resolved.size() < maxOffers) {
            for (TradeSpec spec : fallback) {
                VillagerTrades.ItemListing listing = asListing(spec);
                if (listing != null) resolved.add(listing);
                if (resolved.size() >= maxOffers) break;
            }
        }

        levelTrades.addAll(resolved);
    }

    private static VillagerTrades.ItemListing asListing(TradeSpec spec) {
        Item item = resolveItem(spec.itemId());
        if (item == null) return null;
        if (spec.isBuy()) {
            return (trader, random) -> new MerchantOffer(
                    //? if >=1.21 {
                    new ItemCost(item, spec.itemCount()),
                    //?} else {
                    /*new ItemStack(item, spec.itemCount()),
                    *///?}
                    new ItemStack(Items.EMERALD, spec.emeraldCost()),
                    spec.maxUses(),
                    spec.villagerXp(),
                    PRICE_MULTIPLIER
            );
        }
        return (trader, random) -> new MerchantOffer(
                //? if >=1.21 {
                new ItemCost(Items.EMERALD, spec.emeraldCost()),
                //?} else {
                /*new ItemStack(Items.EMERALD, spec.emeraldCost()),
                *///?}
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

    private static TradeSpec buy(String itemId, int itemCount, int emeraldReward, int maxUses, int villagerXp) {
        return new TradeSpec(true, itemId, emeraldReward, itemCount, maxUses, villagerXp);
    }

    private static TradeSpec sell(String itemId, int emeraldCost, int itemCount, int maxUses, int villagerXp) {
        return new TradeSpec(false, itemId, emeraldCost, itemCount, maxUses, villagerXp);
    }

    private record TradeSpec(boolean isBuy, String itemId, int emeraldCost, int itemCount, int maxUses, int villagerXp) {}
}
