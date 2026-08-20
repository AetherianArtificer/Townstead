package com.aetherianartificer.townstead.work.station;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;

/** The registry behind {@link StationSupply}. One implementation serves the whole engine. */
public final class StationSupplies {

    private static volatile @Nullable StationSupply supply;

    private StationSupplies() {}

    public static void register(StationSupply provider) {
        supply = provider;
    }

    public static boolean pullTool(ServerLevel level, VillagerEntityMCA villager,
                                   Predicate<ItemStack> matcher, BlockPos center, Set<Long> bounds) {
        StationSupply current = supply;
        return current != null && current.pullTool(level, villager, matcher, center, bounds);
    }

    public static int pullDistinct(ServerLevel level, VillagerEntityMCA villager, TagKey<Item> tag,
                                   int max, BlockPos center, Set<Long> bounds) {
        StationSupply current = supply;
        return current == null ? 0 : current.pullDistinct(level, villager, tag, max, center, bounds);
    }

    public static int pullMatching(ServerLevel level, VillagerEntityMCA villager,
                                   Predicate<ItemStack> matcher, int count, BlockPos center, Set<Long> bounds) {
        StationSupply current = supply;
        return current == null ? 0 : current.pullMatching(level, villager, matcher, count, center, bounds);
    }

    public static int storeOutput(ServerLevel level, VillagerEntityMCA villager, ItemStack stack,
                                  BlockPos center, Set<Long> bounds) {
        StationSupply current = supply;
        return current == null ? 0 : current.storeOutput(level, villager, stack, center, bounds);
    }
}
