package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

public final class FarmersDelightCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "farmersdelight";

    @Override
    public String modId() {
        return MOD_ID;
    }

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        // Rice item (places RiceBlock which extends BushBlock, not CropBlock)
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "rice")) return true;
        // Tomato seeds (places BuddingTomatoBlock which extends BushBlock, not CropBlock)
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "tomato_seeds")) return true;
        // Onion (food item that doubles as a plantable BlockItem)
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "onion")) return true;
        // Cabbage seeds
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "cabbage_seeds");
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        if (!ModCompat.matchesLoadedModPath(key, MOD_ID, "tomatoes")) return false;
        return isMatureAge(state);
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();

        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        // Reset vine age to 0 — the vine keeps growing for next harvest
        BlockState reset = state.setValue(ageProp, 0);
        level.setBlock(pos, reset, Block.UPDATE_ALL);

        // Drop 1-2 tomatoes (matching FD's right-click harvest logic)
        List<ItemStack> drops = new ArrayList<>();
        ResourceLocation tomatoId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "tomato");
        BuiltInRegistries.ITEM.getOptional(tomatoId).ifPresent(tomato -> {
            int count = 1 + level.random.nextInt(2);
            drops.add(new ItemStack(tomato, count));
        });
        return drops;
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        ResourceLocation key = above.getBlock().builtInRegistryHolder().key().location();
        // Rice stem (extends BushBlock, not detected by instanceof CropBlock)
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "rice")) return true;
        // Budding tomato (extends BuddingBushBlock/BushBlock, not detected by instanceof CropBlock)
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "budding_tomatoes");
    }

    @Override
    public String patternHintForSeed(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "rice")) return "rice_paddy";
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "tomato_seeds")) return "tomato_garden";
        return null;
    }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) {
        // Rice is planted IN water — the position itself must be a water source
        if (!level.getFluidState(pos).is(FluidTags.WATER)) return false;
        if (!level.getBlockState(pos).is(Blocks.WATER)) return false;
        // Ground below must be suitable for rice
        BlockState below = level.getBlockState(pos.below());
        return below.is(Blocks.DIRT)
                || below.is(Blocks.MUD)
                || below.is(Blocks.FARMLAND)
                || below.is(Blocks.GRASS_BLOCK)
                || below.is(Blocks.CLAY);
    }

    private static boolean isMatureAge(BlockState state) {
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return false;
        int value = state.getValue(ageProp);
        int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
        return value >= max;
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        StateDefinition<?, ?> definition = state.getBlock().getStateDefinition();
        Property<?> property = definition.getProperty("age");
        if (property instanceof IntegerProperty integerProperty) return integerProperty;
        return null;
    }
}
