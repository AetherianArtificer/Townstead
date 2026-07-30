package com.aetherianartificer.townstead.work.station;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.items.IItemHandler;
*///?}
import org.jetbrains.annotations.Nullable;

/**
 * Reading and writing a furnace's slots, whichever way the block chose to expose them.
 *
 * <p>Vanilla and Iron Furnaces are {@link Container}s, so slots are addressed directly. Better
 * Furnaces Reforged is not: its block entity implements its own interfaces and offers an item
 * handler instead. Both mean the same thing by "slot 1", so the difference belongs here rather
 * than in the adapter or in every workstation def.</p>
 *
 * <p>Every write asks permission first. Neither backing type validates on its own — a container's
 * {@code setItem} is a bare list write — and a furnace in a mode that cannot smelt keeps its input
 * slot present while refusing it, so placing without asking silently swallows a villager's food.</p>
 */
interface FurnaceSlotAccess {

    int size();

    ItemStack get(int slot);

    /** Whether the block would accept this stack here, without changing anything. */
    boolean canPlace(int slot, ItemStack stack);

    /** Places one stack; returns false (having changed nothing) if the block refused it. */
    boolean place(int slot, ItemStack stack);

    /** Takes everything in the slot, or an empty stack if there was nothing to take. */
    ItemStack takeAll(int slot);

    /** The best access for this block, or null when it exposes neither shape. */
    static @Nullable FurnaceSlotAccess of(ServerLevel level, BlockPos anchor) {
        BlockEntity be = level.getBlockEntity(anchor);
        if (be == null) return null;
        if (be instanceof Container container) return new ContainerAccess(container);
        IItemHandler handler = BlockInventories.itemHandler(be, level, anchor, null);
        return handler == null ? null : new HandlerAccess(handler);
    }

    /** Vanilla, Iron Furnaces, and anything else backed by a plain container. */
    final class ContainerAccess implements FurnaceSlotAccess {

        private final Container container;

        ContainerAccess(Container container) {
            this.container = container;
        }

        @Override public int size() { return container.getContainerSize(); }

        @Override public ItemStack get(int slot) { return container.getItem(slot); }

        @Override public boolean canPlace(int slot, ItemStack stack) {
            return container.canPlaceItem(slot, stack);
        }

        @Override public boolean place(int slot, ItemStack stack) {
            if (!container.canPlaceItem(slot, stack)) return false;
            container.setItem(slot, stack);
            container.setChanged();
            return true;
        }

        @Override public ItemStack takeAll(int slot) {
            ItemStack held = container.getItem(slot);
            if (held.isEmpty()) return ItemStack.EMPTY;
            container.setItem(slot, ItemStack.EMPTY);
            container.setChanged();
            return held;
        }
    }

    /** Better Furnaces Reforged and anything else that only publishes an item handler. */
    final class HandlerAccess implements FurnaceSlotAccess {

        private final IItemHandler handler;

        HandlerAccess(IItemHandler handler) {
            this.handler = handler;
        }

        @Override public int size() { return handler.getSlots(); }

        @Override public ItemStack get(int slot) { return handler.getStackInSlot(slot); }

        @Override public boolean canPlace(int slot, ItemStack stack) {
            // A simulated insert is the handler's own answer to the question, and taking the whole
            // stack is the only outcome worth calling acceptance.
            return handler.insertItem(slot, stack.copy(), true).isEmpty();
        }

        @Override public boolean place(int slot, ItemStack stack) {
            if (!canPlace(slot, stack)) return false;
            // insertItem returns the leftover rather than mutating the argument, so the result is
            // the only safe thing to read afterwards.
            ItemStack remainder = handler.insertItem(slot, stack.copy(), false);
            return remainder.isEmpty();
        }

        @Override public ItemStack takeAll(int slot) {
            ItemStack held = handler.getStackInSlot(slot);
            if (held.isEmpty()) return ItemStack.EMPTY;
            return handler.extractItem(slot, held.getCount(), false);
        }
    }
}
