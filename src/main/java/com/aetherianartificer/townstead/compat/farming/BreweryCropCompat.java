package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * Let's Do Brewery compat. Hops are a climbing crop: a head block that grows upward along hanging
 * rope and leaves body blocks below it. Every segment ripens on its own and is picked by
 * right-click, so breaking a ripe segment would destroy the plant above it. The seed item differs
 * per version (a separate hops_seeds item, or the hops item itself), so the seed is recognised by
 * the block it places.
 */
public final class BreweryCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "brewery";
    private static final String HEAD_PATH = "hops_crop";
    private static final String BODY_PATH = "hops_crop_body";
    private static final String PRODUCT_PATH = "hops";

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        ResourceLocation block = blockItem.getBlock().builtInRegistryHolder().key().location();
        return MOD_ID.equals(block.getNamespace()) && HEAD_PATH.equals(block.getPath());
    }

    @Override
    public ResourceLocation cropProductFor(ResourceLocation seedId) {
        if (!MOD_ID.equals(seedId.getNamespace())) return null;
        if (!"hops_seeds".equals(seedId.getPath()) && !PRODUCT_PATH.equals(seedId.getPath())) return null;
        return productId();
    }

    @Override
    public boolean isColumnBlock(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return MOD_ID.equals(key.getNamespace())
                && (HEAD_PATH.equals(key.getPath()) || BODY_PATH.equals(key.getPath()));
    }

    @Override
    public Item columnProduct(BlockState state) {
        return isColumnBlock(state) ? BuiltInRegistries.ITEM.getOptional(productId()).orElse(null) : null;
    }

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        if (!isColumnBlock(state)) return false;
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return false;
        int value = state.getValue(ageProp);
        int max = ageProp.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
        return value >= max;
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!shouldPartialHarvest(state)) return List.of();
        IntegerProperty ageProp = findAgeProperty(state);
        if (ageProp == null) return List.of();

        // Brewery resets a body segment that still has the head directly above it to age 2, and
        // everything else to age 1.
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        ResourceLocation above = level.getBlockState(pos.above()).getBlock().builtInRegistryHolder().key().location();
        boolean bodyUnderHead = BODY_PATH.equals(key.getPath())
                && MOD_ID.equals(above.getNamespace()) && HEAD_PATH.equals(above.getPath());
        int resetAge = bodyUnderHead && ageProp.getPossibleValues().contains(2) ? 2 : 1;
        level.setBlock(pos, state.setValue(ageProp, resetAge), Block.UPDATE_CLIENTS);

        List<ItemStack> drops = new ArrayList<>();
        BuiltInRegistries.ITEM.getOptional(productId()).ifPresent(product ->
                drops.add(new ItemStack(product, 1 + level.random.nextInt(2))));
        return drops;
    }

    @Override
    public boolean needsRope(ServerLevel level, BlockPos pos, BlockState state) {
        return isColumnBlock(state) && ClimbingCropRope.needsRope(level, pos, state);
    }

    @Override
    public boolean isRope(ItemStack stack) { return ClimbingCropRope.isRope(stack); }

    @Override
    public boolean applyRope(ServerLevel level, BlockPos pos, BlockState state) {
        return isColumnBlock(state) && ClimbingCropRope.applyRope(level, pos, state);
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) { return false; }

    private static ResourceLocation productId() {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, PRODUCT_PATH);
        //?} else {
        /*return new ResourceLocation(MOD_ID, PRODUCT_PATH);
        *///?}
    }

    private static IntegerProperty findAgeProperty(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("age");
        return property instanceof IntegerProperty integerProperty ? integerProperty : null;
    }
}
