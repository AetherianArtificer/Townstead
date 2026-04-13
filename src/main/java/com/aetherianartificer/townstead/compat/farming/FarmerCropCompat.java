package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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

    /**
     * Places this mod's implementation of the given SoilType at pos. Returns true on success,
     * false if this provider doesn't handle the given type (or its mod isn't loaded). Used as a
     * generic extension point for mod-specific soils beyond vanilla + FD rich soil — e.g.,
     * Farming for Blockheads fertilized farmland variants. The caller is expected to have already
     * consumed the required {@link #soilCreationItem} from the farmer's inventory.
     */
    default boolean placeSoil(com.aetherianartificer.townstead.farming.cellplan.SoilType type,
                               ServerLevel level, BlockPos pos) { return false; }

    /**
     * True if the block at pos already matches this mod's soil for the given SoilType — so the
     * state machine can skip redundant placement (and detect regression when the block no longer
     * matches). Defaults to false; mod-specific soils should override.
     */
    default boolean isExistingSoil(com.aetherianartificer.townstead.farming.cellplan.SoilType type,
                                    ServerLevel level, BlockPos pos) { return false; }

    /** Legacy: same as placeRichSoilTilled. Kept for any external callers. */
    default boolean doCompatTill(ServerLevel level, BlockPos pos) { return placeRichSoilTilled(level, pos); }

    /**
     * Returns the crop-product item ID for a given seed ID, or null if this provider doesn't
     * know about it. Called when the loot-table lookup can't resolve the product — typically for
     * perennial crops whose blocks only drop seeds on break and yield the real product via
     * right-click harvest (FD tomato, YH tea, Vinery grapes, etc.).
     */
    @Nullable
    default net.minecraft.resources.ResourceLocation cropProductFor(net.minecraft.resources.ResourceLocation seedId) { return null; }

    /**
     * Item the farmer must consume to create this mod's variant of the given soil type.
     * Returning null means the soil requires no consumable item beyond tools (e.g., vanilla farmland
     * only needs a hoe, which is already in the farmer's restock list). If an item is returned, the
     * farmer will restock it from nearby chests and shrink one stack when creating the soil.
     */
    @Nullable
    default Item soilCreationItem(SoilType type) { return null; }
}
