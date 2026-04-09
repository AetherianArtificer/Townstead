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
        // Check compat providers first (handles special cases like FD rice)
        if (FarmerCropCompatRegistry.isSeed(new ItemStack(item))) return true;

        // Generic detection: does this item place a crop/bush block?
        Block placedBlock = getPlacedBlock(item);
        if (placedBlock == null) return false;
        return placedBlock instanceof CropBlock || placedBlock instanceof BushBlock;
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
