package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;

public final class CookTradeBackfillTicker {
    private static final String TRADE_BACKFILL_KEY = "cook";
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int TRADES_PER_LEVEL = 2;

    private CookTradeBackfillTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!ModCompat.isLoaded("farmersdelight")) return;
        if (villager.tickCount % CHECK_INTERVAL_TICKS != 37) return;
        if (!FarmersDelightCookAssignment.isExternalCookProfession(villager.getVillagerData().getProfession())) return;

        int currentLevel = villager.getVillagerData().getLevel();
        TownsteadVillager.ProfessionMemory memory = TownsteadVillagers.get(villager).professionMemory();
        int populatedLevel = memory.tradeBackfillLevel(TRADE_BACKFILL_KEY);

        if (populatedLevel >= currentLevel) return;

        Int2ObjectMap<VillagerTrades.ItemListing[]> tradeMap =
                VillagerTrades.TRADES.get(villager.getVillagerData().getProfession());
        if (tradeMap == null || tradeMap.isEmpty()) return;

        MerchantOffers offers = villager.getOffers();
        for (int level = Math.max(1, populatedLevel + 1); level <= currentLevel; level++) {
            VillagerTrades.ItemListing[] listings = tradeMap.get(level);
            if (listings == null || listings.length == 0) continue;

            ArrayList<VillagerTrades.ItemListing> pool = new ArrayList<>(Arrays.asList(listings));
            int added = 0;
            while (added < TRADES_PER_LEVEL && !pool.isEmpty()) {
                VillagerTrades.ItemListing listing = pool.remove(villager.getRandom().nextInt(pool.size()));
                MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
                if (offer != null && !hasMatchingOffer(offers, offer)) {
                    offers.add(offer);
                    added++;
                }
            }
        }

        memory.setTradeBackfillLevel(TRADE_BACKFILL_KEY, currentLevel);
    }

    private static boolean hasMatchingOffer(MerchantOffers offers, MerchantOffer candidate) {
        ItemStack result = candidate.getResult();
        for (MerchantOffer existing : offers) {
            //? if >=1.21 {
            if (ItemStack.isSameItemSameComponents(existing.getResult(), result)) return true;
            //?} else {
            /*if (ItemStack.isSameItemSameTags(existing.getResult(), result)) return true;
            *///?}
        }
        return false;
    }
}
