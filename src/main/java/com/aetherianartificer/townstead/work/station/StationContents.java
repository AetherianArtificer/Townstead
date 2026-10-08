package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.storage.StorageSearchContext;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
//? if >=1.21 {
import net.minecraft.world.item.crafting.RecipeHolder;
//?}
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
//?} else if forge {
/*import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
*///?}
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Generic access to the real items held by a data-declared production station. */
public final class StationContents {
    private StationContents() {}

    public static int count(ServerLevel level, BlockPos pos, Item item) {
        if (level == null || pos == null || item == Items.AIR) return 0;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        final int[] total = new int[1];
        final boolean[] handled = new boolean[1];
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            handled[0] = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.is(item)) total[0] += stack.getCount();
            }
        });
        BlockEntity be = level.getBlockEntity(anchor);
        if (!handled[0] && be instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (container.getItem(slot).is(item)) total[0] += container.getItem(slot).getCount();
            }
        }
        return total[0];
    }

    public static boolean hasAny(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return false;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        WorkstationDef def = Workstations.byState(level.getBlockState(anchor));
        int ignoredContainer = def != null && def.role() == StationType.HOT_STATION
                ? def.containerSlot() : -1;
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (slot != ignoredContainer && !handler.getStackInSlot(slot).isEmpty()) return true;
            }
            return false;
        }
        BlockEntity be = level.getBlockEntity(anchor);
        if (be instanceof Container container) {
            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                if (slot != ignoredContainer && !container.getItem(slot).isEmpty()) return true;
            }
        }
        return false;
    }

    public static ItemStack insert(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null || stack.isEmpty()) return stack;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        final ItemStack[] remainder = {stack.copy()};
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            if (!remainder[0].isEmpty()) remainder[0] = StationInventoryOps.insert(handler, remainder[0], false);
        });
        if (remainder[0].getCount() != stack.getCount()) WorksiteStationIndex.invalidate(level, anchor);
        return remainder[0].isEmpty() ? ItemStack.EMPTY : remainder[0];
    }

    public static boolean insertIngredient(ServerLevel level, BlockPos pos, ItemStack stack) {
        if (level == null || pos == null || stack.isEmpty()) return false;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        WorkstationDef def = Workstations.byState(level.getBlockState(anchor));
        if (def == null || def.role() != StationType.HOT_STATION) return false;
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, net.minecraft.core.Direction.UP);
        if (handler == null) handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null) return false;
        int slots = Math.min(def.ingredientSlots(), handler.getSlots());
        for (int slot = 0; slot < slots; slot++) {
            if (!handler.getStackInSlot(slot).isEmpty()) continue;
            ItemStack remainder = handler.insertItem(slot, stack.copy(), false);
            if (remainder.isEmpty()) {
                WorksiteStationIndex.invalidate(level, anchor);
                return true;
            }
        }
        return false;
    }

    public static ItemStack insertContainer(
            ServerLevel level, BlockPos pos, ItemStack stack, boolean simulate
    ) {
        if (level == null || pos == null || stack.isEmpty()) return stack;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        WorkstationDef def = Workstations.byState(level.getBlockState(anchor));
        if (def == null || def.role() != StationType.HOT_STATION) return stack;
        final ItemStack[] remainder = {stack.copy()};
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            if (remainder[0].isEmpty() || def.containerSlot() >= handler.getSlots()) return;
            remainder[0] = handler.insertItem(def.containerSlot(), remainder[0], simulate);
        });
        if (!simulate && remainder[0].getCount() != stack.getCount()) WorksiteStationIndex.invalidate(level, anchor);
        return remainder[0].isEmpty() ? ItemStack.EMPTY : remainder[0];
    }

    public static int containerCount(ServerLevel level, BlockPos pos, Item item) {
        if (level == null || pos == null || item == Items.AIR) return 0;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        WorkstationDef def = Workstations.byState(level.getBlockState(anchor));
        if (def == null || def.role() != StationType.HOT_STATION) return 0;
        final int[] best = new int[1];
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            if (def.containerSlot() >= handler.getSlots()) return;
            ItemStack stack = handler.getStackInSlot(def.containerSlot());
            if (stack.is(item)) best[0] = Math.max(best[0], stack.getCount());
        });
        return best[0];
    }

    public static int extract(ServerLevel level, BlockPos pos, Item item, int amount) {
        if (level == null || pos == null || item == Items.AIR || amount <= 0) return 0;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        final int[] removed = new int[1];
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            for (int slot = 0; slot < handler.getSlots() && removed[0] < amount; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.is(item)) continue;
                removed[0] += handler.extractItem(slot, amount - removed[0], false).getCount();
            }
        });
        if (removed[0] > 0) WorksiteStationIndex.invalidate(level, anchor);
        return removed[0];
    }

    public static boolean canExtract(ServerLevel level, BlockPos pos, Item item, int amount) {
        if (level == null || pos == null || item == Items.AIR || amount <= 0) return false;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        final int[] available = new int[1];
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            for (int slot = 0; slot < handler.getSlots() && available[0] < amount; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.is(item)) available[0] += handler.extractItem(slot, amount - available[0], true).getCount();
            }
        });
        return available[0] >= amount;
    }

    public static boolean hasOutput(ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds) {
        if (outputIds == null || outputIds.isEmpty()) return false;
        for (ResourceLocation id : outputIds) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR && count(level, pos, item) > 0 && canExtract(level, pos, item, 1)) return true;
        }
        return false;
    }

    public static List<ItemStack> extractMatching(
            ServerLevel level, BlockPos pos, Set<ResourceLocation> outputIds
    ) {
        if (level == null || pos == null || outputIds == null || outputIds.isEmpty()) return List.of();
        BlockPos anchor = StationCapacities.anchor(level, pos);
        List<ItemStack> extracted = new ArrayList<>();
        new StorageSearchContext(level).forEachUniqueItemHandler(anchor, (side, handler) -> {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                ResourceLocation id = stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (id == null || !outputIds.contains(id)) continue;
                ItemStack taken = handler.extractItem(slot, stack.getCount(), false);
                if (!taken.isEmpty()) extracted.add(taken);
            }
        });
        if (!extracted.isEmpty()) WorksiteStationIndex.invalidate(level, anchor);
        return extracted;
    }

    public static boolean matchesRecipe(ServerLevel level, BlockPos pos, DiscoveredRecipe recipe) {
        if (level == null || pos == null || recipe == null) return false;
        BlockPos anchor = StationCapacities.anchor(level, pos);
        BlockEntity be = level.getBlockEntity(anchor);
        Object source = recipe.source();
        if (be == null || source == null) return false;
        //? if >=1.21 {
        if (source instanceof RecipeHolder<?> holder) source = holder.value();
        //?}
        try {
            Method inventoryMethod = be.getClass().getMethod("getInventory");
            Object inventory = inventoryMethod.invoke(be);
            if (inventory == null) return false;
            Object wrapper = RecipeWrapper.class.getConstructors()[0].newInstance(inventory);
            for (Method method : source.getClass().getMethods()) {
                if (!method.getName().equals("matches") || method.getParameterCount() != 2) continue;
                try {
                    Object result = method.invoke(source, wrapper, level);
                    if (result instanceof Boolean value) return value;
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static String describeInputs(ServerLevel level, BlockPos pos) {
        if (level == null || pos == null) return "";
        BlockPos anchor = StationCapacities.anchor(level, pos);
        WorkstationDef def = Workstations.byState(level.getBlockState(anchor));
        if (def == null || def.role() != StationType.HOT_STATION) return "";
        IItemHandler handler = BlockInventories.itemHandler(level, anchor, null);
        if (handler == null) return "";
        List<String> parts = new ArrayList<>();
        for (int slot = 0; slot < Math.min(def.ingredientSlots(), handler.getSlots()); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) parts.add(stack.getHoverName().getString() + " x" + stack.getCount());
        }
        if (def.containerSlot() < handler.getSlots()) {
            ItemStack stack = handler.getStackInSlot(def.containerSlot());
            if (!stack.isEmpty()) parts.add("container: " + stack.getHoverName().getString() + " x" + stack.getCount());
        }
        return String.join(", ", parts);
    }
}
