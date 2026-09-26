package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmandcharm.FarmAndCharmCropCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class FarmerCropCompatRegistry {
    private static final List<FarmerCropCompat> PROVIDERS = List.of(
            new FarmersDelightCropCompat(),
            new FarmAndCharmCropCompat(),
            new YoukaiHomecomingCropCompat(),
            new PeruvianDelightCropCompat(),
            new VineryCropCompat(),
            new BreweryCropCompat(),
            new FarmingForBlockheadsCompat(),
            new CreepyDelightCropCompat(),
            new CauponaCropCompat(),
            new CobblemonCropCompat()
    );

    private FarmerCropCompatRegistry() {}

    public static boolean hasAnyLoadedProvider() {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (ModCompat.isLoaded(provider.modId())) return true;
        }
        return false;
    }

    public static boolean isSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isSeed(stack)) return true;
        }
        return false;
    }

    public static boolean excludeAsSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.excludeAsSeed(stack)) return true;
        }
        return false;
    }

    /** True if any loaded provider plants this seed's crop on top of a water source (e.g. medicinal leek). */
    public static boolean plantsOnWaterSurface(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.plantsOnWaterSurface(stack)) return true;
        }
        return false;
    }

    public static boolean shouldPartialHarvest(BlockState state) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.shouldPartialHarvest(state)) return true;
        }
        return false;
    }

    public static List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.shouldPartialHarvest(state)) {
                return provider.doPartialHarvest(level, pos, state);
            }
        }
        return List.of();
    }

    public static boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isExistingFarmSoil(level, pos)) return true;
        }
        return false;
    }

    public static boolean isPlantableSpot(ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isPlantableSpot(level, pos)) return true;
        }
        return false;
    }

    /** Tallest crop column the farmer tends from the ground. */
    public static final int MAX_COLUMN_HEIGHT = 8;

    public static boolean isColumnBlock(BlockState state) {
        if (state.isAir()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isColumnBlock(state)) return true;
        }
        return false;
    }

    /**
     * The position just above the ground under pos, found by walking down through column segments
     * and air. For a plain crop this is pos itself. Planned-soil, farm-radius, and navigation
     * checks use the base, so a tall pole, the empty space above it, and a panel hung over the
     * cell all count as that one cell.
     */
    public static BlockPos columnBase(ServerLevel level, BlockPos pos) {
        BlockPos base = pos;
        for (int i = 0; i < MAX_COLUMN_HEIGHT; i++) {
            BlockPos below = base.below();
            BlockState belowState = level.getBlockState(below);
            if (!belowState.isAir() && !isColumnBlock(belowState)) break;
            base = below;
        }
        return base;
    }

    /** True if the stack places a column segment (a stem, a lattice), so the farmer keeps it as a work material. */
    public static boolean isColumnBlockItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem
                && isColumnBlock(blockItem.getBlock().defaultBlockState());
    }

    public static net.minecraft.world.item.Item columnProduct(BlockState state) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            net.minecraft.world.item.Item product = provider.columnProduct(state);
            if (product != null) return product;
        }
        return null;
    }

    public static boolean isBareSupport(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isBareSupport(level, pos, state)) return true;
        }
        return false;
    }

    public static boolean canPlantOnSupport(ServerLevel level, BlockPos pos, BlockState state, ItemStack seed) {
        if (seed == null || seed.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.canPlantOnSupport(level, pos, state, seed)) return true;
        }
        return false;
    }

    public static boolean plantOnSupport(ServerLevel level, BlockPos pos, BlockState state, ItemStack seed) {
        if (seed == null || seed.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.canPlantOnSupport(level, pos, state, seed)) {
                return provider.plantOnSupport(level, pos, state, seed);
            }
        }
        return false;
    }

    /** True if any loaded mod gives the farmer a support to build, which gates the Trellis soil in the palette. */
    public static boolean hasTrellisProvider() {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (ModCompat.isLoaded(provider.modId()) && provider.providesTrellis()) return true;
        }
        return false;
    }

    public static net.minecraft.world.item.Item trellisIcon() {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            net.minecraft.world.item.Item icon = provider.trellisIcon();
            if (icon != null) return icon;
        }
        return null;
    }

    public static boolean growsOnTrellis(ItemStack seed) {
        if (seed == null || seed.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.growsOnTrellis(seed)) return true;
        }
        return false;
    }

    public static boolean isTrellisSupportItem(ItemStack stack, ItemStack seed) {
        if (stack == null || stack.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isTrellisSupportItem(stack, seed)) return true;
        }
        return false;
    }

    public static boolean isTrellisSupportBlock(BlockState state, ItemStack seed) {
        if (state.isAir()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isTrellisSupportBlock(state, seed)) return true;
        }
        return false;
    }

    public static boolean placeTrellisSupport(ServerLevel level, BlockPos pos, ItemStack supportItem, ItemStack seed,
                                               com.aetherianartificer.townstead.farming.cellplan.TrellisSpec spec) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isTrellisSupportItem(supportItem, seed)) {
                return provider.placeTrellisSupport(level, pos, supportItem, spec);
            }
        }
        return false;
    }

    public static boolean needsRope(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.needsRope(level, pos, state)) return true;
        }
        return false;
    }

    public static boolean isRope(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isRope(stack)) return true;
        }
        return false;
    }

    public static boolean applyRope(ServerLevel level, BlockPos pos, BlockState state) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.needsRope(level, pos, state)) return provider.applyRope(level, pos, state);
        }
        return false;
    }

    public static boolean isCompatibleSoil(ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isCompatibleSoil(level, pos)) return true;
        }
        return false;
    }

    public static boolean placeRichSoilTilled(ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.placeRichSoilTilled(level, pos)) return true;
        }
        return false;
    }

    public static boolean placeRichSoil(ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.placeRichSoil(level, pos)) return true;
        }
        return false;
    }

    public static boolean doCompatTill(ServerLevel level, BlockPos pos) {
        return placeRichSoilTilled(level, pos);
    }

    public static boolean placeSoil(com.aetherianartificer.townstead.farming.cellplan.SoilType type,
                                     ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.placeSoil(type, level, pos)) return true;
        }
        return false;
    }

    public static boolean isExistingSoil(com.aetherianartificer.townstead.farming.cellplan.SoilType type,
                                          ServerLevel level, BlockPos pos) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.isExistingSoil(type, level, pos)) return true;
        }
        return false;
    }

    /** True if any loaded provider can place the given soil type — used to gate palette visibility. */
    public static boolean canAnyProviderPlaceSoil(com.aetherianartificer.townstead.farming.cellplan.SoilType type) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            if (provider.soilCreationItem(type) != null) return true;
        }
        return false;
    }

    public static net.minecraft.resources.ResourceLocation cropProductFor(net.minecraft.resources.ResourceLocation seedId) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            net.minecraft.resources.ResourceLocation product = provider.cropProductFor(seedId);
            if (product != null) return product;
        }
        return null;
    }

    public static net.minecraft.world.item.Item soilCreationItem(com.aetherianartificer.townstead.farming.cellplan.SoilType type) {
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            net.minecraft.world.item.Item item = provider.soilCreationItem(type);
            if (item != null) return item;
        }
        return null;
    }

    public static String patternHintForSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (FarmerCropCompat provider : PROVIDERS) {
            if (!ModCompat.isLoaded(provider.modId())) continue;
            String hint = provider.patternHintForSeed(stack);
            if (hint != null) return hint;
        }
        return null;
    }
}
