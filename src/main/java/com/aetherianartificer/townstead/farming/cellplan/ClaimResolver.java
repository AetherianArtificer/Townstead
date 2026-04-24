package com.aetherianartificer.townstead.farming.cellplan;

import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import com.aetherianartificer.townstead.farming.CropProductResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inspects the live world at a cell and derives the (SoilType, seedId) pair the farmer should
 * plan for, replacing a {@link SoilType#CLAIM} marker with a concrete soil/seed assignment.
 * <p>Runs on the server when a plan with claim markers is saved.</p>
 */
public final class ClaimResolver {
    private ClaimResolver() {}

    public record Resolution(SoilType soil, String seed) {}

    /**
     * Resolves a claim for the cell at {@code postPos + (xOffset, _, zOffset)}. Scans the column
     * for farmland/rich-soil/water and any crop above, and produces the matching assignment.
     * Returns null if nothing claimable exists (the caller may drop the CLAIM entirely).
     */
    public static Resolution resolve(ServerLevel level, BlockPos postPos, int xOffset, int zOffset) {
        int wx = postPos.getX() + xOffset;
        int wz = postPos.getZ() + zOffset;
        int baseY = postPos.getY();

        // Scan ±16 Y for a meaningful column state. Priority:
        //   1. Rice in water → WATER + rice_panicle
        //   2. Water surface (any) → WATER + AUTO
        //   3. Crop above farmland → FARMLAND/RICH_SOIL_TILLED + matching seed
        //   4. Mushroom/sapling on rich_soil → RICH_SOIL + that seed
        //   5. Farmland alone → FARMLAND + AUTO
        //   6. Rich soil alone → RICH_SOIL_TILLED + AUTO (tilled variant — for crops)
        //   7. Dirt/grass → FARMLAND + AUTO (farmer will till it)
        for (int dy = 16; dy >= -16; dy--) {
            BlockPos groundPos = new BlockPos(wx, baseY + dy, wz);
            BlockState groundState = level.getBlockState(groundPos);
            BlockPos above = groundPos.above();
            BlockState aboveState = level.getBlockState(above);

            // Water column: look for rice in the water block itself
            if (level.getFluidState(groundPos).is(FluidTags.WATER)) {
                String riceSeed = inferWaterCrop(groundState, aboveState, level, groundPos);
                return new Resolution(SoilType.WATER, riceSeed != null ? riceSeed : SeedAssignment.AUTO);
            }

            // Is this a valid "ground" block worth considering?
            boolean isFarmland = groundState.getBlock() instanceof FarmBlock;
            boolean isCompatSoil = FarmerCropCompatRegistry.isCompatibleSoil(level, groundPos);
            boolean isPlainDirt = groundState.is(Blocks.DIRT)
                    || groundState.is(Blocks.GRASS_BLOCK)
                    || groundState.is(Blocks.COARSE_DIRT)
                    || groundState.is(Blocks.DIRT_PATH)
                    || groundState.is(Blocks.MUD);
            if (!isFarmland && !isCompatSoil && !isPlainDirt) continue;

            // Crop on top?
            String cropSeed = inferSeedFromCropBlock(level, aboveState, above);

            // Determine soil type: tilled-rich if compat + farmland; plain rich if compat + dirt-like; etc.
            SoilType soil;
            if (isCompatSoil && isFarmland) {
                soil = SoilType.RICH_SOIL_TILLED;
            } else if (isCompatSoil) {
                soil = SoilType.RICH_SOIL;
            } else if (isFarmland) {
                soil = SoilType.FARMLAND;
            } else {
                soil = SoilType.FARMLAND; // dirt/grass → farmer will till
            }

            return new Resolution(soil, cropSeed != null ? cropSeed : SeedAssignment.AUTO);
        }
        return null;
    }

    private static String inferWaterCrop(BlockState waterState, BlockState aboveState, ServerLevel level, BlockPos waterPos) {
        // Check the water block itself (rice paddy — block IS water, rice grown INTO it)
        ResourceLocation waterBlockId = BuiltInRegistries.BLOCK.getKey(waterState.getBlock());
        if (waterBlockId != null && "farmersdelight".equals(waterBlockId.getNamespace())
                && (waterBlockId.getPath().contains("rice") || waterBlockId.getPath().contains("paddy"))) {
            // Return the seed (farmersdelight:rice), not the harvest product (rice_panicle).
            // Claim → rice_panicle wouldn't replant because our FD compat treats rice as the
            // plantable and excludes rice_panicle — the farmer would never find it in inventory.
            //? if >=1.21 {
            Item riceSeed = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("farmersdelight", "rice"));
            //?} else {
            /*Item riceSeed = BuiltInRegistries.ITEM.get(new ResourceLocation("farmersdelight", "rice"));
            *///?}
            if (riceSeed != Items.AIR) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(riceSeed);
                if (id != null) return id.toString();
            }
        }
        return null;
    }

    private static String inferSeedFromCropBlock(ServerLevel level, BlockState cropState, BlockPos cropPos) {
        if (cropState.isAir()) return null;
        Block block = cropState.getBlock();
        // Skip non-crop stuff
        if (!(block instanceof CropBlock) && !(block instanceof StemBlock)
                && !isKnownPlantBlock(block)) return null;

        // Query loot drops to find what seed/planting item would produce this block
        CropProductResolver resolver = CropProductResolver.get(level);
        Item product = resolver.getCropProduct(cropState, level, cropPos);
        if (product == null || product == Items.AIR) return null;

        // Find which seed ID maps to this product via the resolver's palette
        for (java.util.Map.Entry<String, String> entry : resolver.getPalette().entrySet()) {
            if (product == BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(entry.getValue()))) {
                return entry.getKey();
            }
        }

        // Fallback: if the block is a BlockItem, its own item IS the seed
        Item asItem = block.asItem();
        if (asItem != Items.AIR && asItem instanceof BlockItem) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(asItem);
            if (id != null) return id.toString();
        }
        return null;
    }

    private static boolean isKnownPlantBlock(Block block) {
        // Mushrooms, bushblock-based plants from mods. Accept anything that's a BlockItem's placed block.
        return block.asItem() != Items.AIR;
    }

    /** Processes all CLAIM entries in the plan, replacing them with resolved soil/seed pairs. */
    public static CellPlan resolveAll(ServerLevel level, BlockPos postPos, CellPlan plan) {
        CellPlan.Builder builder = plan.toBuilder();
        for (java.util.Map.Entry<Integer, SoilType> entry : plan.soilPlan().entrySet()) {
            if (entry.getValue() != SoilType.CLAIM) continue;
            int key = entry.getKey();
            String existingSeed = plan.seedPlan().get(key);
            boolean preserveSeed = SeedAssignment.isExplicitSeed(existingSeed) || SeedAssignment.PROTECTED.equals(existingSeed);
            int xOff = CellPlan.unpackX(key);
            int zOff = CellPlan.unpackZ(key);
            Resolution r = resolve(level, postPos, xOff, zOff);
            if (r == null) {
                // Nothing claimable here — drop the CLAIM marker entirely.
                builder.removeSoil(key);
                if (!preserveSeed) builder.removeSeed(key);
                continue;
            }
            builder.rawSoil(key, r.soil());
            builder.rawSeed(key, preserveSeed ? existingSeed : r.seed());
        }
        return builder.build();
    }
}
