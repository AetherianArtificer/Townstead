package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Peruvian's Delight compat — excludes the world-gen "silvestre" (wild) variants from the seed list
 * so they don't appear as plantable palette entries alongside the real seeds/foods.
 */
public final class PeruvianDelightCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "peruviansdelight";

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) { return false; }

    @Override
    public boolean excludeAsSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        if (!MOD_ID.equals(key.getNamespace())) return false;
        // "_silvestre" = Spanish for "wild"; these are world-gen bushes, not farmable seeds.
        return key.getPath().endsWith("_silvestre");
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) { return false; }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) { return List.of(); }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }
}
