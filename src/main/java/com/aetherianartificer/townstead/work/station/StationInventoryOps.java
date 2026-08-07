package com.aetherianartificer.townstead.work.station;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.items.IItemHandler;
*///?}

/** Loader-neutral item-stack and inventory primitives used by every production station. */
public final class StationInventoryOps {
    private StationInventoryOps() {}

    //? if >=1.21 {
    public static ItemStack copyOne(ItemStack stack) { return stack.copyWithCount(1); }
    public static ItemStack copyWithCount(ItemStack stack, int count) { return stack.copyWithCount(count); }
    public static boolean sameItemAndComponents(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }
    //?} else {
    /*public static ItemStack copyOne(ItemStack stack) { ItemStack copy = stack.copy(); copy.setCount(1); return copy; }
    public static ItemStack copyWithCount(ItemStack stack, int count) { ItemStack copy = stack.copy(); copy.setCount(count); return copy; }
    public static boolean sameItemAndComponents(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameTags(a, b);
    }
    *///?}

    public static int count(SimpleContainer inventory, Item item) {
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) total += inventory.getItem(slot).getCount();
        }
        return total;
    }

    public static boolean consume(SimpleContainer inventory, Item item, int needed) {
        return removeUpTo(inventory, item, needed) >= needed;
    }

    public static int removeUpTo(SimpleContainer inventory, Item item, int maximum) {
        int removed = 0;
        for (int slot = 0; slot < inventory.getContainerSize() && removed < maximum; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(maximum - removed, stack.getCount());
            stack.shrink(take);
            removed += take;
        }
        return removed;
    }

    public static ItemStack insert(IItemHandler handler, ItemStack stack, boolean simulate) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            remainder = handler.insertItem(slot, remainder, simulate);
            if (remainder.isEmpty()) return ItemStack.EMPTY;
        }
        return remainder;
    }
}
