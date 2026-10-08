package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.WorkIngredients;

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
public final class WorksiteStationSupply implements StationSupply {

    private WorksiteStationSupply() {}

    public static void bootstrap() {
        StationSupplies.register(new WorksiteStationSupply());
    }

    @Override
    public boolean pullTool(ServerLevel level, VillagerEntityMCA villager, Predicate<ItemStack> matcher,
                            BlockPos center, Set<Long> bounds) {
        return WorkIngredients.pullToolMatching(level, villager, matcher, center, bounds);
    }

    @Override
    public int pullDistinct(ServerLevel level, VillagerEntityMCA villager, TagKey<Item> tag, int max,
                            BlockPos center, Set<Long> bounds) {
        return WorkIngredients.pullDistinctTagItems(level, villager, tag, max, center, bounds);
    }

    @Override
    public int pullMatching(ServerLevel level, VillagerEntityMCA villager, Predicate<ItemStack> matcher,
                            int count, BlockPos center, Set<Long> bounds) {
        int pulled = 0;
        while (pulled < count
                && WorkIngredients.pullToolMatching(level, villager, matcher, center, bounds)) {
            pulled++;
        }
        return pulled;
    }

    @Override
    public int storeOutput(ServerLevel level, VillagerEntityMCA villager, ItemStack stack,
                           BlockPos center, Set<Long> bounds) {
        // A copy, because the resolver shrinks what it is given and the seam's contract is
        // "tell me how many fit" — the caller shrinks its own stack by the answer.
        return WorkIngredients.storeOutputInWorksiteStorage(level, villager, stack.copy(), center, bounds);
    }
}
