package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.block.CropDetection;
import com.aetherianartificer.townstead.compat.farming.FarmerCropCompatRegistry;
import com.aetherianartificer.townstead.farming.cellplan.SoilType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Server-side resolver that maps seed items to their crop products using loot tables.
 * Cached per MinecraftServer, invalidated on datapack reload.
 */
public final class CropProductResolver {
    private static final Map<MinecraftServer, CropProductResolver> BY_SERVER = new WeakHashMap<>();

    private final Map<String, String> seedToProduct = new HashMap<>();  // seed registry ID → product registry ID
    private final Map<Block, Item> blockProductCache = new HashMap<>(); // crop block → primary product
    private final Map<String, Set<SoilType>> seedSoilCompat = new HashMap<>(); // seed registry ID → compatible soil types

    private CropProductResolver(ServerLevel level) {
        initialize(level);
    }

    public static CropProductResolver get(ServerLevel level) {
        MinecraftServer server = level.getServer();
        synchronized (BY_SERVER) {
            CropProductResolver existing = BY_SERVER.get(server);
            if (existing != null) return existing;
            CropProductResolver built = new CropProductResolver(level);
            BY_SERVER.put(server, built);
            return built;
        }
    }

    /** Invalidates all per-server caches. Called on datapack reload. */
    public static void invalidate() {
        synchronized (BY_SERVER) {
            BY_SERVER.clear();
        }
    }

    /**
     * Returns the seed→product palette for sending to the client.
     */
    public Map<String, String> getPalette() {
        return Map.copyOf(seedToProduct);
    }

    /**
     * Returns the soil types this seed can be planted on. Derived once at init from the
     * crop's block class and any mod-compat pattern hint.
     * <p>Empty set means "unknown, plant anywhere"; never null.</p>
     */
    public Set<SoilType> getCompatibleSoils(String seedId) {
        // Always derive fresh — cached values could be stale if a mod updates its compat hints
        // between server lifetimes. The derivation is cheap (registry lookup + compat hint check).
        ResourceLocation rl;
        try {
            //? if >=1.21 {
            rl = ResourceLocation.parse(seedId);
            //?} else {
            /*rl = new ResourceLocation(seedId);
            *///?}
        } catch (Exception e) { return EnumSet.of(SoilType.FARMLAND, SoilType.RICH_SOIL_TILLED); }
        Item item = BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) return EnumSet.of(SoilType.FARMLAND, SoilType.RICH_SOIL_TILLED);
        Block placed = getPlacedBlock(item);
        return placed != null
                ? deriveCompatibleSoils(item, placed)
                : EnumSet.of(SoilType.FARMLAND, SoilType.RICH_SOIL_TILLED);
    }

    public Set<SoilType> getCompatibleSoils(Item seedItem) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(seedItem);
        return key == null ? EnumSet.of(SoilType.FARMLAND, SoilType.RICH_SOIL_TILLED) : getCompatibleSoils(key.toString());
    }

    /** Returns the whole seed→compatible-soils map (for client sync). */
    public Map<String, Set<SoilType>> getSoilCompatMap() {
        return Map.copyOf(seedSoilCompat);
    }

    /**
     * Returns the primary crop product for a block state at a position.
     * Used during grid scanning for individual cells.
     */
    public Item getCropProduct(BlockState state, ServerLevel level, BlockPos pos) {
        Block block = state.getBlock();
        Item cached = blockProductCache.get(block);
        if (cached != null) return cached;

        // Query loot table with max-age state for best results
        BlockState queryState = state;
        if (block instanceof CropBlock crop) {
            queryState = crop.getStateForAge(crop.getMaxAge());
        }

        List<ItemStack> drops = Block.getDrops(queryState, level, pos, null);

        // Find the primary non-seed drop
        Item seedItem = block.asItem();
        Item product = null;
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (drop.getItem() == seedItem) continue;
            // Skip known seed items (wheat_seeds from wheat, etc.)
            if (isSeedItem(drop.getItem())) continue;
            product = drop.getItem();
            break;
        }

        // If no non-seed drop found, use the first drop
        if (product == null && !drops.isEmpty()) {
            product = drops.get(0).getItem();
        }

        // Fallback to block item
        if (product == null || product == Items.AIR) {
            product = seedItem != Items.AIR ? seedItem : null;
        }

        if (product != null) blockProductCache.put(block, product);
        return product;
    }

    private void initialize(ServerLevel level) {
        seedToProduct.clear();
        blockProductCache.clear();
        seedSoilCompat.clear();

        for (String seedId : CropDetection.getAllPlantableSeeds()) {
            //? if >=1.21 {
            ResourceLocation rl = ResourceLocation.parse(seedId);
            //?} else {
            /*ResourceLocation rl = new ResourceLocation(seedId);
            *///?}
            Item seedItem = BuiltInRegistries.ITEM.get(rl);
            if (seedItem == Items.AIR) continue;

            // Get the block this seed places
            Block placedBlock = getPlacedBlock(seedItem);
            if (placedBlock == null) continue;

            // FD rice is both the seed and the final food product (rice_panicle is an intermediate
            // drop). Display as "Rice" in the palette instead of "Rice Panicle".
            if ("farmersdelight".equals(rl.getNamespace()) && "rice".equals(rl.getPath())) {
                seedToProduct.put(seedId, seedId);
                blockProductCache.put(placedBlock, seedItem);
                seedSoilCompat.put(seedId, deriveCompatibleSoils(seedItem, placedBlock));
                continue;
            }

            // Query loot table for the crop product
            BlockState maxAgeState;
            if (placedBlock instanceof CropBlock crop) {
                maxAgeState = crop.getStateForAge(crop.getMaxAge());
            } else {
                maxAgeState = placedBlock.defaultBlockState();
            }

            List<ItemStack> drops = Block.getDrops(maxAgeState, level, BlockPos.ZERO, null);

            // Find primary non-seed product
            Item product = null;
            for (ItemStack drop : drops) {
                if (drop.isEmpty()) continue;
                if (drop.getItem() == seedItem) continue;
                if (isSeedItem(drop.getItem())) continue;
                product = drop.getItem();
                break;
            }

            // If loot table only yields seeds, try vanilla hardcoded mappings
            // (melon stem drops melon_seeds, not melon — the fruit grows as a separate block)
            if (product == null) {
                product = vanillaFallback(seedItem);
            }
            // Final fallback: items like carrot/potato that are both seed and crop
            if (product == null) {
                product = seedItem;
            }

            if (product != Items.AIR) {
                ResourceLocation productId = BuiltInRegistries.ITEM.getKey(product);
                if (productId != null) {
                    seedToProduct.put(seedId, productId.toString());
                    blockProductCache.put(placedBlock, product);
                }
            }

            seedSoilCompat.put(seedId, deriveCompatibleSoils(seedItem, placedBlock));
        }
    }

    /**
     * Determines which soil types this seed can be planted on, derived automatically from:
     * <ol>
     *   <li>A mod-compat provider's pattern hint (e.g., FD rice → "rice_paddy" → WATER)</li>
     *   <li>The placed block's class hierarchy (CropBlock/StemBlock → FARMLAND+RICH_SOIL)</li>
     *   <li>Safe default (FARMLAND+RICH_SOIL) for unknown crop types</li>
     * </ol>
     */
    private static Set<SoilType> deriveCompatibleSoils(Item seedItem, Block placedBlock) {
        ItemStack stack = new ItemStack(seedItem);
        String hint = FarmerCropCompatRegistry.patternHintForSeed(stack);
        if ("rice_paddy".equals(hint)) {
            return EnumSet.of(SoilType.WATER);
        }
        // Mushrooms grow on untilled rich soil (plain dirt variant).
        if (placedBlock instanceof net.minecraft.world.level.block.MushroomBlock) {
            return EnumSet.of(SoilType.RICH_SOIL);
        }
        // Saplings: untilled rich soil (FD boost) or any standard dirt-type. Skipping for now since isPlantableSeed filters them out.
        if (placedBlock instanceof CropBlock
                || placedBlock instanceof StemBlock
                || placedBlock instanceof AttachedStemBlock
                || placedBlock instanceof BushBlock) {
            return EnumSet.of(SoilType.FARMLAND, SoilType.RICH_SOIL_TILLED);
        }
        return EnumSet.of(SoilType.FARMLAND, SoilType.RICH_SOIL_TILLED);
    }

    private static Block getPlacedBlock(Item item) {
        if (item instanceof BlockItem blockItem) return blockItem.getBlock();
        if (item instanceof ItemNameBlockItem nameBlockItem) return nameBlockItem.getBlock();
        return null;
    }

    /**
     * Vanilla crops where the loot table doesn't include the actual product
     * (e.g., melon stem drops seeds, not melons — the melon fruit is a separate block).
     */
    private static Item vanillaFallback(Item seedItem) {
        if (seedItem == Items.WHEAT_SEEDS) return Items.WHEAT;
        if (seedItem == Items.BEETROOT_SEEDS) return Items.BEETROOT;
        if (seedItem == Items.MELON_SEEDS) return Items.MELON;
        if (seedItem == Items.PUMPKIN_SEEDS) return Items.PUMPKIN;
        // FD rice is both seed and food — no suffix stripping, return itself.
        ResourceLocation fdKey = BuiltInRegistries.ITEM.getKey(seedItem);
        if (fdKey != null && "farmersdelight".equals(fdKey.getNamespace())
                && "rice".equals(fdKey.getPath())) {
            return seedItem;
        }
        // For modded seeds: try stripping _seeds/_seed suffix and looking up the base item
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(seedItem);
        if (key != null) {
            String ns = key.getNamespace();
            String path = key.getPath();
            String baseName = path;
            if (baseName.endsWith("_seeds")) baseName = baseName.substring(0, baseName.length() - 6);
            else if (baseName.endsWith("_seed")) baseName = baseName.substring(0, baseName.length() - 5);
            else return null;
            // Try various product names
            for (String suffix : new String[]{"", "_beans", "_fruit", "_berry", "_berries", "_leaves", "_leaf"}) {
                //? if >=1.21 {
                ResourceLocation productId = ResourceLocation.fromNamespaceAndPath(ns, baseName + suffix);
                //?} else {
                /*ResourceLocation productId = new ResourceLocation(ns, baseName + suffix);
                *///?}
                Item product = BuiltInRegistries.ITEM.get(productId);
                if (product != Items.AIR && product != seedItem) return product;
            }
        }
        return null;
    }

    private static boolean isSeedItem(Item item) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        if (key == null) return false;
        String path = key.getPath();
        return path.endsWith("_seeds") || path.endsWith("_seed");
    }
}
