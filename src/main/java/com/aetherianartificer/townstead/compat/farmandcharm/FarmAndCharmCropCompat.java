package com.aetherianartificer.townstead.compat.farmandcharm;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farming.FarmerCropCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Let's Do Farm & Charm compat. Most cultivated crops are picked up by Townstead's generic
 * detector, but tomatoes use a custom climbing crop block instead of CropBlock/BushBlock. Keep
 * wild_* forage plants out of the Field Post palette; they are flower/tall-grass style world-gen
 * plants rather than reproducible farm-plot crops.
 */
public final class FarmAndCharmCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "farm_and_charm";

    private static final Set<String> PLANTABLES = Set.of(
            "oat_seeds",
            "barley_seeds",
            "kernels",
            "lettuce_seeds",
            "tomato_seeds",
            "strawberry_seeds",
            "onion"
    );

    private static final Map<String, String> SEED_TO_PRODUCT = Map.ofEntries(
            Map.entry("tomato_seeds", "tomato"),
            Map.entry("lettuce_seeds", "lettuce"),
            Map.entry("strawberry_seeds", "strawberry"),
            Map.entry("oat_seeds", "oat"),
            Map.entry("barley_seeds", "barley"),
            Map.entry("kernels", "corn"),
            Map.entry("onion", "onion")
    );

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        return MOD_ID.equals(key.getNamespace()) && PLANTABLES.contains(key.getPath());
    }

    @Override
    public ResourceLocation cropProductFor(ResourceLocation seedId) {
        if (!MOD_ID.equals(seedId.getNamespace())) return null;
        String productPath = SEED_TO_PRODUCT.get(seedId.getPath());
        if (productPath == null) return null;
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, productPath);
        //?} else {
        /*return new ResourceLocation(MOD_ID, productPath);
        *///?}
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        if (!ModCompat.matchesLoadedModPath(key, MOD_ID, "tomato_crop")
                && !ModCompat.matchesLoadedModPath(key, MOD_ID, "tomato_crop_body")) {
            return false;
        }
        return isMatureAge(state);
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        BlockState reset = state.setValue(ageProp, 1);
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
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public String patternHintForSeed(ItemStack stack) {
        if (stack.isEmpty()) return null;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        if (ModCompat.matchesLoadedModPath(key, MOD_ID, "tomato_seeds")) return "tomato_garden";
        return null;
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
