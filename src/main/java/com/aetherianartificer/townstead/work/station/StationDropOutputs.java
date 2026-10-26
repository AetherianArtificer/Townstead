package com.aetherianartificer.townstead.work.station;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Output entities produced near any workstation, independent of the originating mod. */
public final class StationDropOutputs {
    private StationDropOutputs() {}

    public static boolean has(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        return pos != null && outputIds != null && !outputIds.isEmpty()
                && !matching(level, StationCapacities.anchor(level, pos), outputIds, null).isEmpty();
    }

    public static List<ItemStack> collect(
            ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds
    ) {
        if (pos == null || outputIds == null || outputIds.isEmpty()) return List.of();
        List<ItemEntity> drops = matching(level, StationCapacities.anchor(level, pos), outputIds, null);
        return collect(drops);
    }

    /** Collect station drops only when they remain inside the owning worksite's columns. */
    public static List<ItemStack> collectWithinWorksite(
            ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds, Set<Long> worksiteBounds
    ) {
        if (pos == null || outputIds == null || outputIds.isEmpty()
                || worksiteBounds == null || worksiteBounds.isEmpty()) return List.of();
        List<ItemEntity> drops = matching(
                level, StationCapacities.anchor(level, pos), outputIds, worksiteBounds);
        return collect(drops);
    }

    private static List<ItemStack> collect(List<ItemEntity> drops) {
        if (drops.isEmpty()) return List.of();
        List<ItemStack> collected = new ArrayList<>();
        for (ItemEntity drop : drops) {
            ItemStack stack = drop.getItem().copy();
            if (stack.isEmpty()) continue;
            drop.discard();
            collected.add(stack);
        }
        return collected;
    }

    private static List<ItemEntity> matching(
            ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds,
            Set<Long> worksiteBounds
    ) {
        AABB area = new AABB(pos).inflate(3.0, 2.0, 3.0);
        return level.getEntitiesOfClass(ItemEntity.class, area, entity -> {
            if (worksiteBounds != null && !insideWorksiteColumn(entity.blockPosition(), worksiteBounds)) {
                return false;
            }
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) return false;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && outputIds.contains(id);
        });
    }

    /**
     * Worksite extents describe walkable cells, while an item may rest above a counter in the
     * same x/z column. Vertical tolerance handles that without granting reach through a wall into
     * a horizontally adjacent workplace.
     */
    public static boolean insideWorksiteColumn(BlockPos pos, Set<Long> worksiteBounds) {
        if (pos == null || worksiteBounds == null || worksiteBounds.isEmpty()) return false;
        for (int dy = -3; dy <= 3; dy++) {
            if (worksiteBounds.contains(pos.offset(0, dy, 0).asLong())) return true;
        }
        return false;
    }
}
