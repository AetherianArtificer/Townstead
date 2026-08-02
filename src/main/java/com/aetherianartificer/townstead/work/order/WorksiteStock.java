package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.Worksites;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

/**
 * Counting what a worksite has on its shelves.
 *
 * <p>"In stock here" means inside the worksite's own extent, which is the same definition for a
 * smithy as for a kitchen — that is the whole reason the extent lives on the record. Counted on
 * demand rather than cached: this runs when a screen opens or a job is chosen, not on a tick.</p>
 */
public final class WorksiteStock {

    private WorksiteStock() {}

    public static int count(ServerLevel level, Worksite site, ResourceLocation item,
                            Order.CountScope scope) {
        if (item == null) return 0;
        // Via the record, so a room-bound site is not measured around a position unpacked from its
        // building id. Getting this wrong reads an empty stock and never stops producing.
        Set<Long> extent = Worksites.extentOf(level, site);
        int[] total = {0};
        eachStack(level, extent, stack -> {
            if (stack.isEmpty()) return;
            if (item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && OrderStackFilters.counts(item, stack)) {
                total[0] += stack.getCount();
            }
        });
        // "The village" widens the count to every store the village knows, minus the shelves
        // this worksite just counted for itself.
        if (scope == Order.CountScope.VILLAGE) {
            total[0] += VillageStores.count(level, site.villageId(), item, extent);
        }
        return total[0];
    }

    /** The same count over a tag: every member on the shelves, summed. */
    public static int countTag(ServerLevel level, Worksite site, ResourceLocation tagId,
                               Order.CountScope scope) {
        if (tagId == null) return 0;
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, tagId);
        Set<Long> extent = Worksites.extentOf(level, site);
        int[] total = {0};
        eachStack(level, extent, stack -> {
            if (stack.isEmpty() || !stack.is(tag)) return;
            // A member the settings forbid producing must not satisfy the set either, or
            // stored sapient meat quietly fills a "keep 10 cooked meats" line nobody may touch.
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!OrderTags.permitted(id) || !OrderStackFilters.counts(id, stack)) return;
            total[0] += stack.getCount();
        });
        if (scope == Order.CountScope.VILLAGE) {
            total[0] += VillageStores.countTag(level, site.villageId(), tagId, extent);
        }
        return total[0];
    }

    /** How many of this item the worker is holding. See {@code WorksiteOrders.contextFor}. */
    public static int carried(net.conczin.mca.entity.VillagerEntityMCA villager, ResourceLocation item) {
        if (item == null) return 0;
        int total = 0;
        var inv = villager.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty()) continue;
            if (item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && OrderStackFilters.counts(item, stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** The same over a tag, skipping members the settings forbid, exactly as the shelves do. */
    public static int carriedTag(net.conczin.mca.entity.VillagerEntityMCA villager, ResourceLocation tagId) {
        if (tagId == null) return 0;
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, tagId);
        int total = 0;
        var inv = villager.getInventory();
        for (int slot = 0; slot < inv.getContainerSize(); slot++) {
            ItemStack stack = inv.getItem(slot);
            if (stack.isEmpty() || !stack.is(tag)) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!OrderTags.permitted(id) || !OrderStackFilters.counts(id, stack)) continue;
            total += stack.getCount();
        }
        return total;
    }

    /**
     * Visits every stack on the extent's shelves by walking each covered chunk's block-entity
     * map rather than asking the level about every position. An extent is thousands of positions
     * and a chunk holds a handful of block entities, and this runs on villagers' work-selection
     * ticks — position-by-position lookups here were a measurable frame cost, not a nicety.
     *
     * <p>Vanilla containers speak for themselves; a block that only offers an item handler
     * (Sophisticated Storage's barrels) is read through that. One view per block, and never an
     * aggregator — a storage controller answers for a whole network of shelves that are already
     * being counted individually.</p>
     */
    static void eachStack(ServerLevel level, Set<Long> extent,
                          java.util.function.Consumer<ItemStack> visit) {
        if (extent.isEmpty()) return;
        Set<Long> chunks = new java.util.HashSet<>();
        for (long packed : extent) {
            chunks.add(net.minecraft.world.level.ChunkPos.asLong(
                    BlockPos.getX(packed) >> 4, BlockPos.getZ(packed) >> 4));
        }
        for (long chunkKey : chunks) {
            net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(
                    net.minecraft.world.level.ChunkPos.getX(chunkKey),
                    net.minecraft.world.level.ChunkPos.getZ(chunkKey));
            if (chunk == null) continue;
            for (var entry : chunk.getBlockEntities().entrySet()) {
                if (!extent.contains(entry.getKey().asLong())) continue;
                BlockEntity be = entry.getValue();
                if (aggregator(be.getBlockState())) continue;
                if (be instanceof Container container) {
                    for (int slot = 0; slot < container.getContainerSize(); slot++) {
                        visit.accept(container.getItem(slot));
                    }
                } else {
                    visitHandler(level, entry.getKey(), be, visit);
                }
            }
        }
    }

    /** Blocks that answer for a whole network of other shelves. Counting must skip them. */
    static boolean aggregator(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(AGGREGATORS);
    }

    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>
            AGGREGATORS = net.minecraft.tags.TagKey.create(
                    net.minecraft.core.registries.Registries.BLOCK, rl("townstead:storage_aggregators"));

    private static void visitHandler(ServerLevel level, BlockPos pos, BlockEntity be,
                                     java.util.function.Consumer<ItemStack> visit) {
        //? if >=1.21 {
        net.neoforged.neoforge.items.IItemHandler handler = level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
        //?} else {
        /*net.minecraftforge.items.IItemHandler handler = be.getCapability(
                net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER, null)
                .orElse(null);
        *///?}
        if (handler == null) return;
        for (int i = 0; i < handler.getSlots(); i++) {
            visit.accept(handler.getStackInSlot(i));
        }
    }

    private static ResourceLocation rl(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }

    /**
     * How many villagers a per-villager target scales against. Village-wide, because "one stew each"
     * is a statement about the village rather than about whoever happens to be in the kitchen.
     */
    public static int villagers(ServerLevel level, Worksite site) {
        if (site.villageId() == Worksite.NO_VILLAGE) return 0;
        //? if >=1.21 {
        for (net.conczin.mca.server.world.data.Village village
                : net.conczin.mca.server.world.data.VillageManager.get(level)) {
        //?} else {
        /*for (net.conczin.mca.server.world.data.Village village
                : net.conczin.mca.server.world.data.VillageManager.get(level)) {
        *///?}
            if (village.getId() == site.villageId()) return village.getResidentsUUIDs().toList().size();
        }
        return 0;
    }
}
