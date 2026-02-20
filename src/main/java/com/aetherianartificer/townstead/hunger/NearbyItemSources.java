package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

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
        ContainerSlot best = null;

        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {

            if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (isProcessingContainer(level, pos, be)) continue;
            if (be instanceof Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (!matcher.test(stack)) continue;
                    int score = scorer.applyAsInt(stack);
                    double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (isBetter(best, dist, score)) {
                        best = new ContainerSlot(pos.immutable(), container, false, i, score, dist, null);
                    }
                }
            }

            if (be != null) {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!matcher.test(stack)) continue;
                        int score = scorer.applyAsInt(stack);
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (isBetter(best, dist, score)) {
                            best = new ContainerSlot(pos.immutable(), null, true, i, score, dist, null);
                        }
                    }
                }
                for (Direction side : Direction.values()) {
                    handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                    if (handler == null) continue;
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStackInSlot(i);
                        if (!matcher.test(stack)) continue;
                        int score = scorer.applyAsInt(stack);
                        double dist = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                        if (isBetter(best, dist, score)) {
                            best = new ContainerSlot(pos.immutable(), null, true, i, score, dist, side);
                        }
                    }
                }
            }
        }

        return best;
    }

    public static ItemStack extractOne(ServerLevel level, ContainerSlot slotRef) {
        if (slotRef == null || slotRef.slot() < 0 || slotRef.pos() == null) return ItemStack.EMPTY;

        if (slotRef.isItemHandler()) {
            Direction side = slotRef.side();
            IItemHandler handler = side != null ? level.getCapability(Capabilities.ItemHandler.BLOCK, slotRef.pos(), side) : null;
            ItemStack extracted = extractOneFromHandler(handler, slotRef.slot());
            if (!extracted.isEmpty()) return extracted;

            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, slotRef.pos(), null);
            extracted = extractOneFromHandler(handler, slotRef.slot());
            if (!extracted.isEmpty()) return extracted;

            for (Direction dir : Direction.values()) {
                if (side != null && dir == side) continue;
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, slotRef.pos(), dir);
                extracted = extractOneFromHandler(handler, slotRef.slot());
                if (!extracted.isEmpty()) return extracted;
            }
            return ItemStack.EMPTY;
        }

        Container container = slotRef.container();
        if (container == null || slotRef.slot() >= container.getContainerSize()) return ItemStack.EMPTY;
        ItemStack stack = container.getItem(slotRef.slot());
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack extracted = stack.copyWithCount(1);
        stack.shrink(1);
        container.setChanged();
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
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                center.offset(horizontalRadius, verticalRadius, horizontalRadius))) {

            if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;

            BlockEntity be = level.getBlockEntity(pos);
            // Exclude processing containers from generic storage insertion.
            // Production tasks (e.g. butcher smoker workflow) target these explicitly.
            if (isProcessingContainer(level, pos, be)) continue;
            if (be instanceof Container container) {
                insertIntoContainer(container, stack);
                if (stack.isEmpty()) return true;
            }

            if (be != null) {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    for (int i = 0; i < handler.getSlots(); i++) {
                        stack = handler.insertItem(i, stack, false);
                        if (stack.isEmpty()) return true;
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    public static boolean isProcessingContainer(ServerLevel level, BlockPos pos, BlockEntity be) {
        if (be instanceof AbstractFurnaceBlockEntity) return true;
        if (level.getBlockState(pos).is(BlockTags.CAMPFIRES)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        if (id == null) return false;
        if ("farmersdelight".equals(id.getNamespace())) {
            String path = id.getPath();
            return "cooking_pot".equals(path)
                    || "skillet".equals(path)
                    || "stove".equals(path)
                    || "cutting_board".equals(path);
        }
        return false;
    }

    private static void insertIntoContainer(Container container, ItemStack stack) {
        // Merge first.
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
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
            container.setItem(i, stack.copyWithCount(move));
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
