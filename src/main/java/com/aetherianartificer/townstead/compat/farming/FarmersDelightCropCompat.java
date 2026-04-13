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
        // Rice — the actual seed planted into water. `rice_panicle` is the harvested drop, not the seed.
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "rice")) return true;
        // Tomato seeds (places BuddingTomatoBlock which extends BushBlock, not CropBlock)
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "tomato_seeds")) return true;
        // Onion (food item that doubles as a plantable BlockItem)
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "onion")) return true;
        // Cabbage seeds
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "cabbage_seeds");
    }

    @Override
    public boolean excludeAsSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        // Non-plantable items that our generic class-based detection would otherwise flag as seeds.
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "rice_panicle")) return true; // harvest product
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "sandy_shrub")) return true; // decorative, world-gen
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "wild_rice")) return true; // world-gen only
        return false;
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        // Tomato vine — partial harvest when ripe, resets to age 0.
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "tomatoes")) {
            return isMatureAge(state);
        }
        // Mushroom colonies — harvestable at any age > 0; each harvest yields one mushroom and
        // decrements age (matching the vanilla shear interaction on MushroomColonyBlock).
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "red_mushroom_colony")
                || ModCompat.matchesLoadedModPath(key, MOD_ID, "brown_mushroom_colony")) {
            IntegerProperty ageProp = findAgeProperty(state);
            return ageProp != null && state.getValue(ageProp) > 0;
        }
        return false;
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();

        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();

        // Mushroom colony — decrement age, drop one of the matching mushroom.
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "red_mushroom_colony")
                || ModCompat.matchesLoadedModPath(key, MOD_ID, "brown_mushroom_colony")) {
            int age = state.getValue(ageProp);
            BlockState dec = state.setValue(ageProp, Math.max(0, age - 1));
            level.setBlock(pos, dec, Block.UPDATE_ALL);
            net.minecraft.world.item.Item mushroom = ModCompat.matchesLoadedModPath(key, MOD_ID, "red_mushroom_colony")
                    ? net.minecraft.world.item.Items.RED_MUSHROOM
                    : net.minecraft.world.item.Items.BROWN_MUSHROOM;
            return java.util.List.of(new ItemStack(mushroom));
        }

        // Tomato vine — reset age to 0 and drop 1-2 tomatoes.
        BlockState reset = state.setValue(ageProp, 0);
        level.setBlock(pos, reset, Block.UPDATE_ALL);

        List<ItemStack> drops = new ArrayList<>();
        //? if >=1.21 {
        ResourceLocation tomatoId = ResourceLocation.fromNamespaceAndPath(MOD_ID, "tomato");
        //?} else {
        /*ResourceLocation tomatoId = new ResourceLocation(MOD_ID, "tomato");
        *///?}
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

    @Override
    public boolean isCompatibleSoil(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "rich_soil")
                || ModCompat.matchesLoadedModPath(key, MOD_ID, "rich_soil_farmland");
    }

    @Override
    public boolean placeRichSoilTilled(ServerLevel level, BlockPos pos) {
        return placeFromId(level, pos, "rich_soil_farmland");
    }

    @Override
    public boolean placeRichSoil(ServerLevel level, BlockPos pos) {
        return placeFromId(level, pos, "rich_soil");
    }

    private static boolean placeFromId(ServerLevel level, BlockPos pos, String path) {
        if (!ModCompat.isLoaded(MOD_ID)) return false;
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(MOD_ID, path);
        *///?}
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null) return false;
        return level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
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
