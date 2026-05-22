package com.aetherianartificer.townstead.compat.farming;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.List;

/**
 * Cobblemon compat.
 *
 * <p>Cobblemon's farm crops are all vanilla-style {@code CropBlock}s (or, for galarica nuts, a
 * {@code BushBlock} with an age property), so the generic detector already lists them in the
 * Field Post palette and they plant/harvest on farmland with no help: vivichoke, revival herb,
 * the mints, and hearty grains (whose land-plantable tag includes farmland). Two crops need
 * special handling:</p>
 *
 * <ul>
 *   <li><b>Medicinal leek</b> — a {@code CropBlock} that grows on the surface of water rather than
 *       submerged like rice. Its {@code mayPlaceOn} requires a full water source directly below the
 *       crop (water is in {@code cobblemon:medicinal_leek_plantable}), so the leek sits at
 *       {@code soilPos.above()} of a WATER-painted cell. We mark it as a paddy crop (so it routes to
 *       WATER soil compatibility and through the same plant gate as FD rice) and flag it as an
 *       on-surface crop so the farmer plants and harvests it one block above the water.</li>
 *   <li><b>Galarica nut bush</b> — a perennial {@code BushBlock} that pops nuts on interaction and
 *       resets, instead of being destroyed. Since it isn't a {@code CropBlock}, the generic harvest
 *       path never fires; we drive it through the partial-harvest hooks like FD tomato / YH tea.</li>
 * </ul>
 */
public final class CobblemonCropCompat implements FarmerCropCompat {
    private static final String MOD_ID = "cobblemon";

    private static final int NUT_BUSH_MATURE_AGE = 3;       // NutBushBlock.MAX_AGE
    private static final int NUT_BUSH_AFTER_HARVEST_AGE = 1; // vanilla right-click resets to age 1

    @Override
    public String modId() { return MOD_ID; }

    @Override
    public boolean isSeed(ItemStack stack) {
        // Every Cobblemon crop is a CropBlock/BushBlock-with-age BlockItem, which the generic
        // detector already recognizes — nothing extra to opt in.
        return false;
    }

    // --- Medicinal leek: a CropBlock that grows on top of a water source -------------------------

    @Override
    public String patternHintForSeed(ItemStack stack) {
        // Tag the leek as a paddy crop. This reuses the existing water-crop machinery: WATER soil
        // compatibility (CropProductResolver) and the water-planting gate (HarvestWorkTask), exactly
        // like FD rice. The on-surface placement offset is the only thing that differs, handled by
        // plantsOnWaterSurface below.
        return isLeek(stack) ? "rice_paddy" : null;
    }

    @Override
    public boolean plantsOnWaterSurface(ItemStack stack) {
        return isLeek(stack);
    }

    @Override
    public boolean isPlantableSpot(ServerLevel level, BlockPos pos) {
        // The leek's surface spot: open space with a full water source directly below it. Mirrors
        // MedicinalLeekBlock.mayPlaceOn (minecraft:water is in MEDICINAL_LEEK_PLANTABLE, and it
        // requires a full source). This is the soilPos.above() of a WATER-painted cell; on a normal
        // farmland cell the block below is farmland, not water, so this returns false there.
        if (!level.getBlockState(pos).isAir()) return false;
        FluidState below = level.getFluidState(pos.below());
        return below.is(FluidTags.WATER) && below.isSource();
    }

    // --- Galarica nut bush: a perennial BushBlock harvested by hand, not destroyed ---------------

    @Override
    public boolean shouldPartialHarvest(BlockState state) {
        if (!isGalaricaBush(state)) return false;
        IntegerProperty age = ageProperty(state);
        return age != null && state.getValue(age) >= NUT_BUSH_MATURE_AGE;
    }

    @Override
    public List<ItemStack> doPartialHarvest(ServerLevel level, BlockPos pos, BlockState state) {
        if (!isGalaricaBush(state)) return List.of();
        IntegerProperty age = ageProperty(state);
        if (age == null || state.getValue(age) < NUT_BUSH_MATURE_AGE) return List.of();
        // Match the vanilla right-click harvest: yield 1-2 nuts and reset to the post-harvest stage.
        level.setBlock(pos, state.setValue(age, NUT_BUSH_AFTER_HARVEST_AGE), Block.UPDATE_CLIENTS);
        Item nuts = lookupItem("galarica_nuts");
        if (nuts == null || nuts == Items.AIR) return List.of();
        List<ItemStack> drops = new ArrayList<>(1);
        drops.add(new ItemStack(nuts, 1 + level.random.nextInt(2)));
        return drops;
    }

    @Override
    public boolean isExistingFarmSoil(ServerLevel level, BlockPos pos) { return false; }

    // --- helpers ---------------------------------------------------------------------------------

    private static boolean isLeek(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation key = stack.getItem().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "medicinal_leek");
    }

    private static boolean isGalaricaBush(BlockState state) {
        ResourceLocation key = state.getBlock().builtInRegistryHolder().key().location();
        return ModCompat.matchesLoadedModPath(key, MOD_ID, "galarica_nut_bush");
    }

    private static IntegerProperty ageProperty(BlockState state) {
        Property<?> p = state.getBlock().getStateDefinition().getProperty("age");
        return (p instanceof IntegerProperty ip) ? ip : null;
    }

    private static Item lookupItem(String path) {
        if (!ModCompat.isLoaded(MOD_ID)) return null;
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
        //?} else {
        /*ResourceLocation id = new ResourceLocation(MOD_ID, path);
        *///?}
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }
}
