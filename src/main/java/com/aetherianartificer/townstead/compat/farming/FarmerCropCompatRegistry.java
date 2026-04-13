package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class FarmerCropCompatRegistry {
    private static final List<FarmerCropCompat> PROVIDERS = List.of(
            new FarmersDelightCropCompat(),
            new YoukaiHomecomingCropCompat(),
            new PeruvianDelightCropCompat()
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
