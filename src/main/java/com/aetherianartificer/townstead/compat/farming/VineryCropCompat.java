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
import java.util.Map;

/**
 * Let's Do Vinery compat. Grape bushes are perennials — breaking them only yields seeds, and the
 * actual grape items come from right-click harvest at max age. This provider maps each grape-seed
 * variant to its grape product (for the palette label) and implements partial harvest (for the
 * farmer to collect grapes without destroying the bush).
 *
 * <p>Vinery has eight grape variants across biomes: overworld red/white, jungle red/white,
 * savanna red/white, taiga red/white.</p>
 */
public final class VineryCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "vinery";

    /**
     * Seed item path → grape product path. Both are under the "vinery" namespace.
     * The base overworld variants use the pattern {color}_grape_seeds → {color}_grape, while the
     * biome-specific variants flip to {biome}_grape_seeds_{color} → {biome}_grapes_{color}.
     */
    private static final Map<String, String> SEED_TO_PRODUCT = Map.ofEntries(
            Map.entry("red_grape_seeds", "red_grape"),
            Map.entry("white_grape_seeds", "white_grape"),
            Map.entry("jungle_grape_seeds_red", "jungle_grapes_red"),
            Map.entry("jungle_grape_seeds_white", "jungle_grapes_white"),
            Map.entry("savanna_grape_seeds_red", "savanna_grapes_red"),
            Map.entry("savanna_grape_seeds_white", "savanna_grapes_white"),
            Map.entry("taiga_grape_seeds_red", "taiga_grapes_red"),
            Map.entry("taiga_grape_seeds_white", "taiga_grapes_white")
    );

    /** Crop block path → grape product path. Used at harvest time. */
    private static final Map<String, String> BUSH_TO_PRODUCT = Map.ofEntries(
            Map.entry("red_grape_bush", "red_grape"),
            Map.entry("white_grape_bush", "white_grape"),
            Map.entry("jungle_grape_bush_red", "jungle_grapes_red"),
            Map.entry("jungle_grape_bush_white", "jungle_grapes_white"),
            Map.entry("savanna_grape_bush_red", "savanna_grapes_red"),
            Map.entry("savanna_grape_bush_white", "savanna_grapes_white"),
            Map.entry("taiga_grape_bush_red", "taiga_grapes_red"),
            Map.entry("taiga_grape_bush_white", "taiga_grapes_white")
    );

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) { return false; }

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
        if (!MOD_ID.equals(key.getNamespace())) return false;
        if (!BUSH_TO_PRODUCT.containsKey(key.getPath())) return false;
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return false;
        int value = state.getValue(ageProp);
        int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
        return value >= max;
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        String productPath = BUSH_TO_PRODUCT.get(key.getPath());
        if (productPath == null) return List.of();

        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        // Reset to age 1 — matches Vinery's own right-click harvest behavior, which leaves the
        // bush at a partially-grown state so it regrows into fresh grapes rather than restarting.
        BlockState reset = state.setValue(ageProp, 1);
        level.setBlock(pos, reset, Block.UPDATE_ALL);

        List<ItemStack> drops = new ArrayList<>();
        //? if >=1.21 {
        ResourceLocation productId = ResourceLocation.fromNamespaceAndPath(MOD_ID, productPath);
        //?} else {
        /*ResourceLocation productId = new ResourceLocation(MOD_ID, productPath);
        *///?}
        BuiltInRegistries.ITEM.getOptional(productId).ifPresent(product -> {
            int count = 2 + level.random.nextInt(2);
            drops.add(new ItemStack(product, count));
        });
        return drops;
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }

    private static IntegerProperty findAgeProperty(BlockState state) {
        StateDefinition<?, ?> definition = state.getBlock().getStateDefinition();
        Property<?> property = definition.getProperty("age");
        if (property instanceof IntegerProperty integerProperty) return integerProperty;
        return null;
    }
}
