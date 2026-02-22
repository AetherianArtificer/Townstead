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
}
