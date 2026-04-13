package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public interface FarmerCropCompat {
    String modId();

    boolean isSeed(ItemStack stack);

    /**
     * If true, this item is NOT a seed even if class-based detection would include it
     * (e.g., FD rice is a BlockItem that places a growth-stage block, but the real seed is rice_panicle).
     */
    default boolean excludeAsSeed(ItemStack stack) { return false; }

    boolean shouldPartialHarvest(BlockState state);

    List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state);

    boolean isExistingFarmSoil(ServerLevel level, BlockPos pos);

    boolean isPlantableSpot(ServerLevel level, BlockPos pos);

    default String patternHintForSeed(ItemStack stack) { return null; }

    /** Whether the block at pos is a mod-specific rich/compatible soil (e.g., FD rich soil). */
    default boolean isCompatibleSoil(ServerLevel level, BlockPos pos) { return false; }

    /** Places the tilled rich-soil variant (e.g., FD rich_soil_farmland) at pos. Returns true on success. */
    default boolean placeRichSoilTilled(ServerLevel level, BlockPos pos) { return false; }

    /** Places the untilled rich-soil block (e.g., FD rich_soil) at pos. Returns true on success. */
    default boolean placeRichSoil(ServerLevel level, BlockPos pos) { return false; }

    /** Legacy: same as placeRichSoilTilled. Kept for any external callers. */
    default boolean doCompatTill(ServerLevel level, BlockPos pos) { return placeRichSoilTilled(level, pos); }
}
