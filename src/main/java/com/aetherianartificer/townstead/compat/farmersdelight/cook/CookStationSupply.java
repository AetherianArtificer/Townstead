package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.work.station.StationSupplies;
import com.aetherianartificer.townstead.work.station.StationSupply;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Satisfies station fetch requests out of a cook's own kitchen: their inventory first, then
 * nearby containers, then the kitchen's declared storage. The search bounds come from the caller,
 * so this never reaches past the work site the villager was given.
 */
public final class CookStationSupply implements StationSupply {

    private CookStationSupply() {}

    public static void bootstrap() {
        StationSupplies.register(new CookStationSupply());
    }

    @Override
    public boolean pullTool(ServerLevel level, VillagerEntityMCA villager, Predicate<ItemStack> matcher,
                            BlockPos center, Set<Long> bounds) {
        return IngredientResolver.pullToolMatching(level, villager, matcher, center, bounds);
    }

    @Override
    public int pullDistinct(ServerLevel level, VillagerEntityMCA villager, TagKey<Item> tag, int max,
                            BlockPos center, Set<Long> bounds) {
        return IngredientResolver.pullDistinctTagItems(level, villager, tag, max, center, bounds);
    }
}
