package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.storage.StorageSearchContext;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
//?} else if forge {
/*import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
*///?}


import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class NearbyItemSources {
    private NearbyItemSources() {}

    public record ContainerSlot(BlockPos pos, Container container, boolean isItemHandler, int slot, int score, double distanceSqr, Direction side) {}

    public static ContainerSlot findBestNearbySlot(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                                   Predicate<ItemStack> matcher, ToIntFunction<ItemStack> scorer) {
        return findBestNearbySlot(level, villager, horizontalRadius, verticalRadius, matcher, scorer, villager.blockPosition());
    }

    public static ContainerSlot findBestNearbySlot(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                                   Predicate<ItemStack> matcher, ToIntFunction<ItemStack> scorer, BlockPos center) {
        return NearbyStorageIndex.snapshot(level, center, horizontalRadius, verticalRadius)
                .findBestNearbySlot(villager, center, horizontalRadius, verticalRadius, matcher, scorer);
    }

    /**
     * Collect ALL matching container slots (not just the best).
     * Used when the caller needs to do reachability checks and can't rely on a single result.
     */
    public static void collectMatchingSlots(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                             Predicate<ItemStack> matcher, ToIntFunction<ItemStack> scorer, BlockPos center,
                                             Consumer<ContainerSlot> consumer) {
        NearbyStorageIndex.snapshot(level, center, horizontalRadius, verticalRadius)
                .collectMatchingContainerSlots(villager, center, horizontalRadius, verticalRadius, matcher, scorer, consumer);
    }

    public static void collectBestFoodSlots(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                            BlockPos center, Consumer<ContainerSlot> consumer) {
        NearbyStorageIndex.snapshot(level, center, horizontalRadius, verticalRadius)
                .collectBestFoodContainerSlots(villager, center, horizontalRadius, verticalRadius, consumer);
    }

    public static ContainerSlot findBestNearbyDrinkSlot(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                                        BlockPos center, ToIntFunction<ItemStack> scorer) {
        return NearbyStorageIndex.snapshot(level, center, horizontalRadius, verticalRadius)
                .findBestDrinkNearbySlot(villager, center, horizontalRadius, verticalRadius, scorer);
    }

    public static ItemStack extractOne(ServerLevel level, ContainerSlot slotRef) {
        if (slotRef == null || slotRef.slot() < 0 || slotRef.pos() == null) return ItemStack.EMPTY;

        if (slotRef.isItemHandler()) {
            Direction side = slotRef.side();
            BlockEntity be = level.getBlockEntity(slotRef.pos());
            if (be == null) return ItemStack.EMPTY;
            //? if neoforge {
            IItemHandler handler = side != null ? level.getCapability(Capabilities.ItemHandler.BLOCK, slotRef.pos(), side) : null;
            //?} else if forge {
            /*IItemHandler handler = side != null ? be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElse(null) : null;
            *///?}
            ItemStack extracted = extractOneFromHandler(handler, slotRef.slot());
            if (!extracted.isEmpty()) {
                NearbyStorageIndex.invalidate(level, slotRef.pos());
                return extracted;
            }

            //? if neoforge {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, slotRef.pos(), null);
            //?} else if forge {
            /*handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            *///?}
            extracted = extractOneFromHandler(handler, slotRef.slot());
            if (!extracted.isEmpty()) {
                NearbyStorageIndex.invalidate(level, slotRef.pos());
                return extracted;
            }

            for (Direction dir : Direction.values()) {
                if (side != null && dir == side) continue;
                //? if neoforge {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, slotRef.pos(), dir);
                //?} else if forge {
                /*handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, dir).orElse(null);
                *///?}
                extracted = extractOneFromHandler(handler, slotRef.slot());
                if (!extracted.isEmpty()) {
                    NearbyStorageIndex.invalidate(level, slotRef.pos());
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }

        Container container = slotRef.container();
        if (container == null || slotRef.slot() >= container.getContainerSize()) return ItemStack.EMPTY;
        ItemStack stack = container.getItem(slotRef.slot());
        if (stack.isEmpty()) return ItemStack.EMPTY;
        //? if >=1.21 {
        ItemStack extracted = stack.copyWithCount(1);
        //?} else {
        /*ItemStack extracted = stack.copy(); extracted.setCount(1);
        *///?}
        stack.shrink(1);
        container.setChanged();
        NearbyStorageIndex.invalidate(level, slotRef.pos());
        return extracted;
    }

    public static boolean pullSingleToInventory(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                                Predicate<ItemStack> matcher, ToIntFunction<ItemStack> scorer) {
        return pullSingleToInventory(level, villager, horizontalRadius, verticalRadius, matcher, scorer, villager.blockPosition());
    }

    public static boolean pullSingleToInventory(ServerLevel level, VillagerEntityMCA villager, int horizontalRadius, int verticalRadius,
                                                Predicate<ItemStack> matcher, ToIntFunction<ItemStack> scorer, BlockPos center) {
        ContainerSlot slot = findBestNearbySlot(level, villager, horizontalRadius, verticalRadius, matcher, scorer, center);
        if (slot == null) return false;
        ItemStack extracted = extractOne(level, slot);
        if (extracted.isEmpty()) return false;
        ItemStack remainder = villager.getInventory().addItem(extracted);
        if (remainder.isEmpty()) return true;

        insertIntoNearbyStorage(level, villager, remainder, horizontalRadius, verticalRadius, center);
        if (!remainder.isEmpty()) {
            ItemEntity drop = new ItemEntity(level, villager.getX(), villager.getY() + 0.25, villager.getZ(), remainder.copy());
            drop.setPickUpDelay(0);
            level.addFreshEntity(drop);
        }
        return false;
    }

    public static boolean insertIntoNearbyStorage(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, int horizontalRadius, int verticalRadius) {
        return insertIntoNearbyStorage(level, villager, stack, horizontalRadius, verticalRadius, villager.blockPosition());
    }

    public static boolean insertIntoNearbyStorage(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, int horizontalRadius, int verticalRadius, BlockPos center) {
        if (stack.isEmpty()) return true;
        StorageSearchContext searchContext = new StorageSearchContext(level);
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {

            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            if (observed.protectedStorage()) continue;
            BlockEntity be = observed.blockEntity();
            // Exclude processing containers from generic storage insertion.
            // Production tasks (e.g. butcher smoker workflow) target these explicitly.
            if (isProcessingContainer(observed.state(), be)) continue;
            if (be instanceof Container container) {
                int beforeCount = stack.getCount();
                insertIntoContainer(container, stack);
                if (stack.getCount() != beforeCount) {
                    NearbyStorageIndex.invalidate(level, observed.pos());
                }
                if (stack.isEmpty()) return true;
            }

            if (be != null) {
                IItemHandler handler = searchContext.getItemHandler(observed.pos(), null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        int beforeCount = stack.getCount();
                        stack = handler.insertItem(i, stack, false);
                        if (stack.getCount() != beforeCount) {
                            NearbyStorageIndex.invalidate(level, observed.pos());
                        }
                        if (stack.isEmpty()) return true;
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    /**
     * Insert {@code stack} into any container located inside the given MCA
     * Building's bounding box, ignoring blocks outside the building even
     * when they fall inside the rectangular bounds (the bounding box can
     * span multiple buildings; {@code containsPos} is the authoritative
     * membership check). Mutates {@code stack} in place; returns true if
     * the stack was fully consumed. Useful for production tasks that want
     * outputs to land in storage owned by the same room as the workstation,
     * not "anywhere within a 16-block radius."
     */
    public static boolean insertIntoBuildingStorage(ServerLevel level, VillagerEntityMCA villager,
            ItemStack stack, net.conczin.mca.server.world.data.Building building) {
        if (stack.isEmpty()) return true;
        if (building == null) return false;
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        int minX = Math.min(p0.getX(), p1.getX());
        int minY = Math.min(p0.getY(), p1.getY());
        int minZ = Math.min(p0.getZ(), p1.getZ());
        int maxX = Math.max(p0.getX(), p1.getX());
        int maxY = Math.max(p0.getY(), p1.getY());
        int maxZ = Math.max(p0.getZ(), p1.getZ());
        StorageSearchContext searchContext = new StorageSearchContext(level);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY && !stack.isEmpty(); y++) {
            for (int x = minX; x <= maxX && !stack.isEmpty(); x++) {
                for (int z = minZ; z <= maxZ && !stack.isEmpty(); z++) {
                    cursor.set(x, y, z);
                    if (!building.containsPos(cursor)) continue;
                    StorageSearchContext.ObservedBlock observed = searchContext.observe(cursor);
                    if (observed.protectedStorage()) continue;
                    BlockEntity be = observed.blockEntity();
                    if (be == null) continue;
                    if (isProcessingContainer(observed.state(), be)) continue;
                    if (be instanceof Container container) {
                        int beforeCount = stack.getCount();
                        insertIntoContainer(container, stack);
                        if (stack.getCount() != beforeCount) {
                            NearbyStorageIndex.invalidate(level, observed.pos());
                        }
                        if (stack.isEmpty()) return true;
                    }
                    IItemHandler handler = searchContext.getItemHandler(observed.pos(), null);
                    if (handler != null) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            int beforeCount = stack.getCount();
                            stack = handler.insertItem(i, stack, false);
                            if (stack.getCount() != beforeCount) {
                                NearbyStorageIndex.invalidate(level, observed.pos());
                            }
                            if (stack.isEmpty()) return true;
                        }
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    public static boolean isProcessingContainer(ServerLevel level, BlockPos pos, BlockEntity be) {
        return isProcessingContainer(level.getBlockState(pos), be);
    }

    public static boolean isProcessingContainer(BlockState state, BlockEntity be) {
        if (be instanceof AbstractFurnaceBlockEntity) return true;
        if (state.is(BlockTags.CAMPFIRES)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        String ns = id.getNamespace();
        String path = id.getPath();
        if ("farmersdelight".equals(ns)) {
            return "cooking_pot".equals(path)
                    || "skillet".equals(path)
                    || "stove".equals(path)
                    || "cutting_board".equals(path);
        }
        if ("butchery".equals(ns)) {
            // Butchery's MCreator-generated blocks all ship with internal
            // item slots, regardless of whether the block actually uses
            // them. Generic deposit scans treat those slots as bottomless
            // sinks. Treat every block in the namespace as processing
            // except the freezer, which is the one legitimate storage.
            return !"freezer".equals(path);
        }
        // Exclude blocks that are clearly machines/devices, not storage
        if (path.contains("machine") || path.contains("vending")
                || path.contains("terminal") || path.contains("interface")
                || path.contains("generator") || path.contains("engine")
                || path.contains("press") || path.contains("crusher")
                || path.contains("grinder") || path.contains("centrifuge")
                || path.contains("assembler") || path.contains("processor")) {
            return true;
        }
        return false;
    }

    private static void insertIntoContainer(Container container, ItemStack stack) {
        // Merge first.
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) continue;
            //? if >=1.21 {
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
            //?} else {
            /*if (!ItemStack.isSameItemSameTags(slot, stack)) continue;
            *///?}
            if (!container.canPlaceItem(i, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), slot.getMaxStackSize());
            if (slot.getCount() >= limit) continue;
            int move = Math.min(stack.getCount(), limit - slot.getCount());
            if (move <= 0) continue;
            slot.grow(move);
            stack.shrink(move);
            container.setChanged();
        }

        // Then use empty slots.
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty()) continue;
            if (!container.canPlaceItem(i, stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(), stack.getMaxStackSize()));
            if (move <= 0) continue;
            //? if >=1.21 {
            container.setItem(i, stack.copyWithCount(move));
            //?} else {
            /*ItemStack portion = stack.copy(); portion.setCount(move); container.setItem(i, portion);
            *///?}
            stack.shrink(move);
            container.setChanged();
        }
    }

    private static boolean isBetter(ContainerSlot currentBest, double candidateDist, int candidateScore) {
        if (currentBest == null) return true;
        if (candidateDist < currentBest.distanceSqr() - 4.0) return true;
        return candidateDist < currentBest.distanceSqr() + 4.0 && candidateScore > currentBest.score();
    }

    private static ItemStack extractOneFromHandler(IItemHandler handler, int slot) {
        if (handler == null || slot < 0 || slot >= handler.getSlots()) return ItemStack.EMPTY;
        return handler.extractItem(slot, 1, false);
    }
}
