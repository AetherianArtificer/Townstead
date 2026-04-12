package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public interface FarmerCropCompat {
    String modId();

    boolean isSeed(ItemStack stack);

    boolean shouldPartialHarvest(BlockState state);

    List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state);

    boolean isExistingFarmSoil(ServerLevel level, BlockPos pos);

    boolean isPlantableSpot(ServerLevel level, BlockPos pos);

    default String patternHintForSeed(ItemStack stack) { return null; }

    /** Whether the block at pos is a mod-specific rich/compatible soil (e.g., FD rich soil). */
    default boolean isCompatibleSoil(ServerLevel level, BlockPos pos) { return false; }

    /** Attempts to convert vanilla farmland at pos to a compatible rich soil. Returns true on success. */
    default boolean doCompatTill(ServerLevel level, BlockPos pos) { return false; }
}
