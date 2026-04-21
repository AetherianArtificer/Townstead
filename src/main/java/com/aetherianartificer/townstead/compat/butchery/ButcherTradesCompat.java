package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
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

/**
 * Adds Butchery items to the vanilla butcher trade catalog at Apprentice
 * through Master tiers. Novice is left untouched so vanilla butcher behavior
 * stays intact for users who toggle Butchery off.
 *
 * See {@code docs/design/butchery_integration.md} section 5.
 */
public final class ButcherTradesCompat {
    private static final float PRICE_MULTIPLIER = 0.05F;

    private ButcherTradesCompat() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!ModCompat.isLoaded(ButcheryCompat.MOD_ID)) return;
        if (event.getType() != VillagerProfession.BUTCHER) return;

        // Apprentice: specialty raw cuts. Buy from emeralds, sell to the player.
        addTieredSellTrades(event, 2, 2, List.of(
                spec("butchery:raw_ribeye_steak", 3, 1, 12, 5),
                spec("butchery:raw_sirloin_steak", 3, 1, 12, 5),
                spec("butchery:raw_pork_belly", 3, 1, 12, 5),
                spec("butchery:raw_lamb_sirloin", 3, 1, 12, 5)
        ));

        // Journeyman: sausages, minced meats, tanned hides.
        addTieredSellTrades(event, 3, 2, List.of(
                spec("butchery:raw_sausage", 4, 1, 10, 10),
                spec("butchery:cooked_sausage", 6, 1, 10, 10),
                spec("butchery:raw_beef_mince", 3, 2, 12, 10),
                spec("butchery:cow_hide", 5, 1, 8, 10)
        ));

        // Expert: cooked specialty cuts and brewing-ingredient organs.
        addTieredSellTrades(event, 4, 2, List.of(
                spec("butchery:cooked_ribeye_steak", 6, 1, 8, 15),
                spec("butchery:cooked_pork_belly", 6, 1, 8, 15),
                spec("butchery:cooked_lamb_loin", 6, 1, 8, 15),
                spec("butchery:cooked_heart", 8, 1, 6, 15),
                spec("butchery:cooked_liver", 8, 1, 6, 15)
        ));

        // Master: signature cured and processed goods.
        addTieredSellTrades(event, 5, 2, List.of(
                spec("butchery:cooked_ham", 12, 1, 6, 30),
                spec("butchery:cooked_blood_sausage", 10, 1, 6, 30),
                spec("butchery:cooked_sirloin_steak", 10, 1, 6, 30),
                spec("butchery:cooked_leg_of_lamb", 12, 1, 6, 30)
        ));
    }

    private static void addTieredSellTrades(
            VillagerTradesEvent event,
            int level,
            int maxOffers,
            List<TradeSpec> specs
    ) {
        List<VillagerTrades.ItemListing> levelTrades = event.getTrades().computeIfAbsent(level, k -> new ArrayList<>());
        List<VillagerTrades.ItemListing> resolved = new ArrayList<>();
        for (TradeSpec spec : specs) {
            VillagerTrades.ItemListing listing = asSellListing(spec);
            if (listing != null) resolved.add(listing);
            if (resolved.size() >= maxOffers) break;
        }
        levelTrades.addAll(resolved);
    }

    private static VillagerTrades.ItemListing asSellListing(TradeSpec spec) {
        Item item = resolveItem(spec.itemId());
        if (item == null) return null;
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

    private static TradeSpec spec(String itemId, int emeraldCost, int itemCount, int maxUses, int villagerXp) {
        return new TradeSpec(itemId, emeraldCost, itemCount, maxUses, villagerXp);
    }

    private record TradeSpec(String itemId, int emeraldCost, int itemCount, int maxUses, int villagerXp) {}
}
