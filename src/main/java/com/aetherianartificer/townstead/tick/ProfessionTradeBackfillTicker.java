package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Repairs offers for existing villagers after data-driven profession trades are installed. */
public final class ProfessionTradeBackfillTicker {
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int TRADES_PER_LEVEL = 2;
    private static final List<Policy> POLICIES = List.of(
            new Policy("cook", 37, () -> ModCompat.isLoaded("farmersdelight"),
                    List.of(WorkTaskTypes.COOK, WorkTaskTypes.CHOP), null),
            new Policy("beverage_artisan", 53, () -> true,
                    List.of(WorkTaskTypes.BREW), "brewer")
    );

    private ProfessionTradeBackfillTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        for (Policy policy : POLICIES) {
            tick(villager, policy);
        }
    }

    private static void tick(VillagerEntityMCA villager, Policy policy) {
        if (!policy.enabled().getAsBoolean()) return;
        if (villager.tickCount % CHECK_INTERVAL_TICKS != policy.offset()) return;
        if (!declaresAny(villager, policy.tasks())) return;

        int currentLevel = villager.getVillagerData().getLevel();
        TownsteadVillager.ProfessionMemory memory = TownsteadVillagers.get(villager).professionMemory();
        int populatedLevel = memory.tradeBackfillLevel(policy.memoryKey());
        // Preserve worlds created before the profession was renamed. Once touched, the neutral
        // policy key becomes authoritative and the old value is only a migration source.
        if (policy.legacyMemoryKey() != null) {
            populatedLevel = Math.max(populatedLevel,
                    memory.tradeBackfillLevel(policy.legacyMemoryKey()));
        }
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
                VillagerTrades.ItemListing listing = pool.remove(
                        villager.getRandom().nextInt(pool.size()));
                MerchantOffer offer = listing.getOffer(villager, villager.getRandom());
                if (offer != null && !hasMatchingOffer(offers, offer)) {
                    offers.add(offer);
                    added++;
                }
            }
        }
        memory.setTradeBackfillLevel(policy.memoryKey(), currentLevel);
    }

    private static boolean declaresAny(VillagerEntityMCA villager,
                                       List<ResourceLocation> tasks) {
        for (ResourceLocation task : tasks) {
            if (WorkTaskDeclarations.professionDeclares(
                    villager.getVillagerData().getProfession(), task)) return true;
        }
        return false;
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

    private record Policy(String memoryKey, int offset,
                          java.util.function.BooleanSupplier enabled,
                          List<ResourceLocation> tasks,
                          String legacyMemoryKey) {}
}
