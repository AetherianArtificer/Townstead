package com.aetherianartificer.townstead.work.station;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Fetching what a protocol station needs into the villager's hands.
 *
 * <p>The station engine knows a recipe wants a tool, or a few different garnishes. It does not
 * know where this villager's things live — that is the profession's business (a cook's kitchen,
 * a smith's stores), and the search is bounded by whatever that profession considers its own.
 * So the engine states the need and the profession's supply layer satisfies it.</p>
 *
 * <p>With nothing registered every request politely fails, which is the right answer: a station
 * asked to fetch from nowhere has fetched nothing.</p>
 */
public interface StationSupply {

    /** Pull one item matching {@code matcher} into the villager's inventory. */
    boolean pullTool(ServerLevel level, VillagerEntityMCA villager, Predicate<ItemStack> matcher,
                     BlockPos center, Set<Long> bounds);

    /**
     * Pull up to {@code max} DISTINCT items of {@code tag} into inventory — one of each, variety
     * rather than volume. Returns how many distinct members the inventory now holds.
     */
    int pullDistinct(ServerLevel level, VillagerEntityMCA villager, TagKey<Item> tag, int max,
                     BlockPos center, Set<Long> bounds);
}
