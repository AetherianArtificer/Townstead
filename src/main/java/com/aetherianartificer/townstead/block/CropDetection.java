package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.MushroomBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.TallGrassBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic crop/seed detection utility. Scans the item registry to find all items
 * that can place crop or bush blocks, regardless of which mod adds them.
 */
public final class CropDetection {
    private CropDetection() {}

    private static List<String> cachedPlantableSeeds;

    /**
     * Returns true if the given item places a block that extends CropBlock or BushBlock,
     * or is recognized by any registered FarmerCropCompat provider.
     */
    public static boolean isPlantableSeed(Item item) {
        ItemStack stack = new ItemStack(item);
        // Compat providers can explicitly disqualify items that class-detection would accept.
        if (FarmerCropCompatRegistry.excludeAsSeed(stack)) return false;
        // Check compat providers first (handles special cases like FD rice_panicle)
        if (FarmerCropCompatRegistry.isSeed(stack)) return true;

        // Generic detection: does this item place a crop/bush block?
        Block placedBlock = getPlacedBlock(item);
        if (placedBlock == null) return false;

        // Exclude non-crop bush subclasses: saplings (trees), flowers, tall grass
        if (placedBlock instanceof SaplingBlock) return false;
        if (placedBlock instanceof FlowerBlock) return false;
        if (placedBlock instanceof TallGrassBlock) return false;
        if (placedBlock instanceof DoublePlantBlock) return false;
        // Mushrooms ARE allowed — they plant on rich soil for colony farming.
        if (placedBlock instanceof MushroomBlock) return true;

        if (placedBlock instanceof CropBlock) return true;
        // BushBlock is ambiguous — mods use the class for both actual growing crops (rice, tomato
        // vine, etc.) and decorative food-display blocks (e.g. a 3D "barley" model placed from the
        // food item). Only the growing kind has an "age" property.
        if (placedBlock instanceof BushBlock) {
            return placedBlock.getStateDefinition().getProperty("age") != null;
        }
        return false;
    }

    /**
     * Returns a list of all item registry IDs that are plantable seeds.
     * Result is cached after first call.
     */
    public static List<String> getAllPlantableSeeds() {
        if (cachedPlantableSeeds != null) return cachedPlantableSeeds;

        List<String> seeds = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (isPlantableSeed(item)) {
                ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                seeds.add(key.toString());
            }
        }
        cachedPlantableSeeds = List.copyOf(seeds);
        return cachedPlantableSeeds;
    }

    /**
     * Invalidate the cache (call on datapack reload if needed).
     */
    public static void invalidateCache() {
        cachedPlantableSeeds = null;
    }

    private static Block getPlacedBlock(Item item) {
        if (item instanceof BlockItem blockItem) {
            return blockItem.getBlock();
        }
        if (item instanceof ItemNameBlockItem nameBlockItem) {
            return nameBlockItem.getBlock();
        }
        return null;
    }
}
