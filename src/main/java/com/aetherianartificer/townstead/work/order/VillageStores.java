package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.storage.VillageStorageIndex;
import com.aetherianartificer.townstead.work.site.Worksite;

import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * The village's shelves, counted as one answer.
 *
 * <p>"Counted across the village" means every container the {@link VillageStorageIndex}
 * snapshot knows — the same set villagers already source food from — summed, minus whatever
 * the asking worksite has already counted for itself, so nothing lands twice. Counting is
 * village-wide on purpose while <em>fetching</em> deliberately is not: a worksite works from
 * its own shelves, and the gap between "the village has it" and "it is here" is the Porter's
 * future job, not something the count should paper over.</p>
 */
public final class VillageStores {

    private VillageStores() {}

    /** Sum of one item across the village's stores, skipping containers already counted. */
    public static int count(ServerLevel level, int villageId, ResourceLocation item,
                            Set<Long> alreadyCounted) {
        if (item == null) return 0;
        int total = 0;
        for (VillageStorageIndex.Entry entry : entries(level, villageId)) {
            if (alreadyCounted.contains(entry.pos().asLong())) continue;
            if (WorksiteStock.aggregator(level.getBlockState(entry.pos()))) continue;
            for (VillageStorageIndex.SlotView slot : countableSlots(entry)) {
                ItemStack stack = slot.stack();
                if (stack.isEmpty()) continue;
                if (item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        && OrderStackFilters.counts(item, stack)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** The same over a tag, skipping members the settings forbid, exactly as the shelves do. */
    public static int countTag(ServerLevel level, int villageId, ResourceLocation tagId,
                               Set<Long> alreadyCounted) {
        if (tagId == null) return 0;
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, tagId);
        int total = 0;
        for (VillageStorageIndex.Entry entry : entries(level, villageId)) {
            if (alreadyCounted.contains(entry.pos().asLong())) continue;
            // A controller or IO block answers for a network of shelves counted individually.
            if (WorksiteStock.aggregator(level.getBlockState(entry.pos()))) continue;
            for (VillageStorageIndex.SlotView slot : countableSlots(entry)) {
                ItemStack stack = slot.stack();
                if (stack.isEmpty() || !stack.is(tag)) continue;
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (!OrderTags.permitted(id) || !OrderStackFilters.counts(id, stack)) continue;
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * One view of a block's inventory, not two. The index records every container both as its
     * Container slots and through its item-handler wrapper — right for finding a slot to pull
     * from, and exactly double when summed, which is how 126 cooked meats read as 252. The
     * Container view wins where it exists (it is per-block, so a double chest's halves cannot
     * re-count each other through the combined wrapper); handler slots only speak for blocks
     * that expose nothing else.
     */
    private static List<VillageStorageIndex.SlotView> countableSlots(VillageStorageIndex.Entry entry) {
        List<VillageStorageIndex.SlotView> container = new java.util.ArrayList<>();
        for (VillageStorageIndex.SlotView slot : entry.slots()) {
            if (!slot.itemHandler()) container.add(slot);
        }
        return container.isEmpty() ? entry.slots() : container;
    }

    /** The snapshot's container entries for this village, or nothing when it cannot be found. */
    private static List<VillageStorageIndex.Entry> entries(ServerLevel level, int villageId) {
        if (villageId == Worksite.NO_VILLAGE) return List.of();
        for (Village village : VillageManager.get(level)) {
            if (village.getId() == villageId) {
                return VillageStorageIndex.snapshot(level, village).entries();
            }
        }
        return List.of();
    }
}
