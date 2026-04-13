package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Farming for Blockheads compat. Maps our three {@code FERTILIZED_*} soil types onto FFB's
 * fertilized farmland variants, and declares which fertilizer item creates each.
 */
public final class FarmingForBlockheadsCompat implements FarmerCropCompat {
    private static final String MOD_ID = "farmingforblockheads";

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) { return false; }

    @Override
    public boolean shouldPartialHarvest(BlockState state) { return false; }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) { return List.of(); }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public Item soilCreationItem(SoilType type) {
        if (!ModCompat.isLoaded(MOD_ID)) return null;
        String itemPath = fertilizerItemPath(type);
        if (itemPath == null) return null;
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, itemPath);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(MOD_ID, itemPath);
        *///?}
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? null : item;
    }

    @Override
    public boolean placeSoil(SoilType type, ServerLevel level, BlockPos pos) {
        if (!ModCompat.isLoaded(MOD_ID)) return false;
        String blockPath = fertilizedFarmlandBlockPath(type);
        if (blockPath == null) return false;
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, blockPath);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(MOD_ID, blockPath);
        *///?}
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) return false;
        return level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
    }

    @Override
    public boolean isExistingSoil(SoilType type, ServerLevel level, BlockPos pos) {
        if (!ModCompat.isLoaded(MOD_ID)) return false;
        String blockPath = fertilizedFarmlandBlockPath(type);
        if (blockPath == null) return false;
        ResourceLocation key = level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location();
        return MOD_ID.equals(key.getNamespace()) && blockPath.equals(key.getPath());
    }

    private static String fertilizerItemPath(SoilType type) {
        return switch (type) {
            case FERTILIZED_RICH -> "green_fertilizer";
            case FERTILIZED_HEALTHY -> "red_fertilizer";
            case FERTILIZED_STABLE -> "yellow_fertilizer";
            default -> null;
        };
    }

    private static String fertilizedFarmlandBlockPath(SoilType type) {
        return switch (type) {
            case FERTILIZED_RICH -> "fertilized_farmland_rich";
            case FERTILIZED_HEALTHY -> "fertilized_farmland_healthy";
            case FERTILIZED_STABLE -> "fertilized_farmland_stable";
            default -> null;
        };
    }
}
