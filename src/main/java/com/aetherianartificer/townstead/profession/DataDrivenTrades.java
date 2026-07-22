package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.TradeDef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

//? if neoforge {
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
//?} else if forge {
/*import net.minecraftforge.event.village.VillagerTradesEvent;
*///?}

/**
 * Installs delegating trade listings for every villager profession. Each slot resolves the
 * profession's {@link ProfessionDef} {@code trades} at offer time, so trades are datapack
 * data: reload changes them even though listings register once at startup. Professions whose
 * def declares no trades resolve empty and the slots are inert. Alias professions converge on
 * the owning def, so another mod's equivalent profession sells the same catalogue.
 */
public final class DataDrivenTrades {

    /** Data can define up to this many offers per merchant level. */
    private static final int SLOTS_PER_LEVEL = 8;
    private static final int MAX_LEVEL = 5;

    private DataDrivenTrades() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.getType());
        if (professionId == null) return;
        for (int level = 1; level <= MAX_LEVEL; level++) {
            List<VillagerTrades.ItemListing> listings =
                    event.getTrades().computeIfAbsent(level, key -> new ArrayList<>());
            for (int slot = 0; slot < SLOTS_PER_LEVEL; slot++) {
                listings.add(new DataDrivenListing(professionId, level, slot));
            }
        }
    }

    private record DataDrivenListing(ResourceLocation professionId, int level, int slot)
            implements VillagerTrades.ItemListing {

        @Override
        @Nullable
        public MerchantOffer getOffer(Entity trader, RandomSource random) {
            ProfessionDef def = ProfessionDefs.byId(professionId);
            if (def == null) return null;
            List<TradeDef> trades = tradesFor(def, level);
            if (slot >= trades.size()) return null;
            return offer(trades.get(slot));
        }

        /**
         * Vanilla merchants cap at level 5, so career levels past five sell from the Master
         * tier: the level-5 slots resolve every trade defined at level 5 and above, in order.
         */
        private static List<TradeDef> tradesFor(ProfessionDef def, int merchantLevel) {
            if (merchantLevel < MAX_LEVEL) {
                return def.trades().getOrDefault(merchantLevel, List.of());
            }
            List<Integer> levels = new ArrayList<>();
            for (Integer key : def.trades().keySet()) {
                if (key >= MAX_LEVEL) levels.add(key);
            }
            java.util.Collections.sort(levels);
            List<TradeDef> out = new ArrayList<>();
            for (Integer key : levels) {
                out.addAll(def.trades().get(key));
            }
            return out;
        }

        @Nullable
        private static MerchantOffer offer(TradeDef trade) {
            Item cost = item(trade.costItem());
            Item result = item(trade.resultItem());
            if (cost == null || result == null) return null;
            Item secondary = trade.secondaryCostItem() == null ? null : item(trade.secondaryCostItem());
            if (trade.secondaryCostItem() != null && secondary == null) return null;
            ItemStack resultStack = new ItemStack(result, trade.resultCount());
            //? if >=1.21 {
            net.minecraft.world.item.trading.ItemCost costA =
                    new net.minecraft.world.item.trading.ItemCost(cost, trade.costCount());
            java.util.Optional<net.minecraft.world.item.trading.ItemCost> costB = secondary == null
                    ? java.util.Optional.empty()
                    : java.util.Optional.of(new net.minecraft.world.item.trading.ItemCost(
                            secondary, trade.secondaryCostCount()));
            return new MerchantOffer(costA, costB, resultStack,
                    trade.maxUses(), trade.villagerXp(), trade.priceMultiplier());
            //?} else {
            /*ItemStack costA = new ItemStack(cost, trade.costCount());
            ItemStack costB = secondary == null
                    ? ItemStack.EMPTY : new ItemStack(secondary, trade.secondaryCostCount());
            return new MerchantOffer(costA, costB, resultStack,
                    trade.maxUses(), trade.villagerXp(), trade.priceMultiplier());
            *///?}
        }

        @Nullable
        private static Item item(ResourceLocation id) {
            return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        }
    }
}
