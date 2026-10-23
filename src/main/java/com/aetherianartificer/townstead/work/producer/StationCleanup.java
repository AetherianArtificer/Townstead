package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.station.StationCapacities;
import com.aetherianartificer.townstead.work.station.WorksiteStationIndex;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Set;

/** Recovers real items from a generic inventory-backed station without knowing its mod. */
public final class StationCleanup {
    private StationCleanup() {}

    public static boolean clear(
            ServerLevel level, VillagerEntityMCA villager, BlockPos pos, Set<Long> storageBounds
    ) {
        if (level == null || villager == null || pos == null) return false;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        final boolean[] cleared = new boolean[1];
        final boolean[] sawHandler = new boolean[1];
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            sawHandler[0] = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack present = handler.getStackInSlot(slot);
                if (present.isEmpty()) continue;
                ItemStack extracted = handler.extractItem(slot, present.getCount(), false);
                if (extracted.isEmpty()) continue;
                cleared[0] = true;
                route(level, villager, extracted, anchor, storageBounds);
            }
        });
        BlockEntity be = level.getBlockEntity(anchor);
        if (!sawHandler[0] && be instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                ItemStack stack = container.getItem(slot);
                if (stack.isEmpty()) continue;
                container.setItem(slot, ItemStack.EMPTY);
                cleared[0] = true;
                route(level, villager, stack, anchor, storageBounds);
            }
            if (cleared[0]) container.setChanged();
        }
        if (cleared[0]) WorksiteStationIndex.invalidate(level, anchor);
        return cleared[0];
    }

    private static void route(ServerLevel level, VillagerEntityMCA villager, ItemStack stack,
                              BlockPos anchor, Set<Long> storageBounds) {
        if (stack.isEmpty()) return;
        ItemStack remainder = villager.getInventory().addItem(stack);
        if (remainder.isEmpty()) return;
        ItemEntity drop = new ItemEntity(level, villager.getX(), villager.getY() + 0.25,
                villager.getZ(), remainder.copy());
        drop.setPickUpDelay(0);
        level.addFreshEntity(drop);
    }
}
