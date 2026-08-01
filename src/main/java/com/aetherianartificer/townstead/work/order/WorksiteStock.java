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
        if (extent.isEmpty()) return 0;

        int total = 0;
        for (long packed : extent) {
            BlockPos pos = BlockPos.of(packed);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof Container container)) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                if (item.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    /** The same count over a tag: every member on the shelves, summed. */
    public static int countTag(ServerLevel level, Worksite site, ResourceLocation tagId,
                               Order.CountScope scope) {
        if (tagId == null) return 0;
        Set<Long> extent = Worksites.extentOf(level, site);
        if (extent.isEmpty()) return 0;
        var tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.ITEM, tagId);
        int total = 0;
        for (long packed : extent) {
            BlockEntity blockEntity = level.getBlockEntity(BlockPos.of(packed));
            if (!(blockEntity instanceof Container container)) continue;
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty() || !stack.is(tag)) continue;
                // A member the settings forbid producing must not satisfy the set either, or
                // stored sapient meat quietly fills a "keep 10 cooked meats" line nobody may touch.
                if (!OrderTags.permitted(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                total += stack.getCount();
            }
        }
        return total;
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
