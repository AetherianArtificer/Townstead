package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility provider for Youkai's Homecoming / Youkai's Feasts crops.
 * Covers perennial crops like tea where the plant is right-click harvested (drops leaves, resets age)
 * rather than broken for its drops.
 */
public final class YoukaiHomecomingCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "youkaisfeasts";

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "tea_seeds");
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        // Tea is a DoubleCropBlock; the upper or lower half at max age yields leaves when right-clicked.
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "tea")) {
            IntegerProperty ageProp = findAgeProperty(state);
            if (ageProp == null) return false;
            int value = state.getValue(ageProp);
            int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
            return value >= max;
        }
        return false;
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        if (!ModCompat.matchesLoadedModPath(key, MOD_ID, "tea")) return List.of();

        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        // Tea's TeaCropBlock resets to its "double block start" age (partway through growth) after
        // harvest. We don't have that constant, so reset to 0 and let it regrow — matches what a
        // player would observe after breaking and replanting.
        BlockState reset = state.setValue(ageProp, 0);
        level.setBlock(pos, reset, Block.UPDATE_ALL);

        // Reset the paired half too, if it's the same tea block at pos.above() or pos.below().
        BlockState upper = level.getBlockState(pos.above());
        if (ModCompat.matchesLoadedModPath(upper.getBlock().builtInRegistryHolder().key().location(), MOD_ID, "tea")) {
            IntegerProperty upperAge = findAgeProperty(upper);
            if (upperAge != null) level.setBlock(pos.above(), upper.setValue(upperAge, 0), Block.UPDATE_ALL);
        }

        List<ItemStack> drops = new ArrayList<>();
        ResourceLocation leavesId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "tea_leaves");
        BuiltInRegistries.ITEM.getOptional(leavesId).ifPresent(leaves -> {
            int count = 1 + level.random.nextInt(2);
            drops.add(new ItemStack(leaves, count));
        });
        return drops;
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        ResourceLocation key = above.getBlock().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "tea");
    }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) {
        return false;
    }

    @Override
    public Item soilCreationItem(com.aetherianartificer.townstead.farming.cellplan.SoilType type) {
        return null;
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        StateDefinition<?, ?> definition = state.getBlock().getStateDefinition();
        Property<?> property = definition.getProperty("age");
        if (property instanceof IntegerProperty integerProperty) return integerProperty;
        return null;
    }
}
