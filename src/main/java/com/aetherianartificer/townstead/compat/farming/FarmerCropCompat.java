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

    /**
     * True if this seed's crop is planted ON TOP of a water source (at the block above the water)
     * rather than submerged in it like rice. For these, the farmer places the water source in the
     * WATER-painted cell and the crop one block above it. Cobblemon's medicinal leek is the
     * canonical example. Defaults to false (in-water / on-land behavior).
     */
    default boolean plantsOnWaterSurface(ItemStack stack) { return false; }

    boolean shouldPartialHarvest(BlockState state);

    List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state);

    boolean isExistingFarmSoil(ServerLevel level, BlockPos pos);

    boolean isPlantableSpot(ServerLevel level, BlockPos pos);

    default String patternHintForSeed(ItemStack stack) { return null; }

    /**
     * True if this block is one segment of a vertical crop column that stands on a planned cell:
     * a trellis pole (Vinery grapevine stem) or a climbing crop (hops, Farm &amp; Charm tomatoes).
     * The farmer walks up the column from the cell and treats every segment as part of that cell.
     */
    default boolean isColumnBlock(BlockState state) { return false; }

    /** The item this column segment yields when picked, for the Field Post grid. Null when it has none yet. */
    @Nullable
    default Item columnProduct(BlockState state) { return null; }

    /** True if the block at pos is an empty support that some seed can be planted onto. */
    default boolean isBareSupport(ServerLevel level, BlockPos pos, BlockState state) { return false; }

    /**
     * True if the block at pos is an empty support that accepts this seed by use-on-block
     * (a bare grapevine stem with a trunk below it), as opposed to a seed placed on soil.
     */
    default boolean canPlantOnSupport(ServerLevel level, BlockPos pos, BlockState state, ItemStack seed) { return false; }

    /** Plants the seed onto the support at pos. The caller shrinks the seed stack on success. */
    default boolean plantOnSupport(ServerLevel level, BlockPos pos, BlockState state, ItemStack seed) { return false; }

    /** True if this provider has a support block the farmer can build on a TRELLIS cell. */
    default boolean providesTrellis() { return false; }

    /** Item shown for the Trellis soil in the Field Post palette. Null when this provider has no trellis. */
    @Nullable
    default Item trellisIcon() { return null; }

    /** True if this seed grows on a farmer-built support, so it may be painted on a TRELLIS cell. */
    default boolean growsOnTrellis(ItemStack seed) { return false; }

    /**
     * True if the stack is a support block item the farmer builds on a TRELLIS cell for this seed.
     * An empty seed means the cell has no specific seed and the provider's default support applies.
     */
    default boolean isTrellisSupportItem(ItemStack stack, ItemStack seed) { return false; }

    /** True if the block is a support segment of the kind this seed needs. */
    default boolean isTrellisSupportBlock(BlockState state, ItemStack seed) { return false; }

    /** Places one support segment at pos. The caller shrinks the item stack on success. */
    default boolean placeTrellisSupport(ServerLevel level, BlockPos pos, ItemStack supportItem,
                                         com.aetherianartificer.townstead.farming.cellplan.TrellisSpec spec) { return false; }

    /** True if this climbing-crop segment has no rope yet and would grow taller with it. */
    default boolean needsRope(ServerLevel level, BlockPos pos, BlockState state) { return false; }

    /** True if the stack is the rope this provider's climbing crops accept. */
    default boolean isRope(ItemStack stack) { return false; }

    /** Applies rope to the segment at pos. The caller shrinks the rope stack on success. */
    default boolean applyRope(ServerLevel level, BlockPos pos, BlockState state) { return false; }

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
