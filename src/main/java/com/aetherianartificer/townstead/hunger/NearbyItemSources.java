package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.storage.StorageRoles;
import com.aetherianartificer.townstead.storage.StorageSearchContext;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
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
import net.minecraft.world.level.chunk.LevelChunk;
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
import javax.annotation.Nullable;

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
        return insertIntoNearbyStorage(level, villager, stack, horizontalRadius, verticalRadius,
                villager.blockPosition(), com.aetherianartificer.townstead.storage.StorageUse.OUTPUT);
    }

    public static boolean insertIntoNearbyStorage(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, int horizontalRadius, int verticalRadius, BlockPos center) {
        return insertIntoNearbyStorage(level, villager, stack, horizontalRadius, verticalRadius, center,
                com.aetherianartificer.townstead.storage.StorageUse.OUTPUT);
    }

    public static boolean insertIntoNearbyStorage(ServerLevel level, VillagerEntityMCA villager,
            ItemStack stack, int horizontalRadius, int verticalRadius, BlockPos center,
            com.aetherianartificer.townstead.storage.StorageUse use) {
        return insertIntoNearbyStorage(level, villager, stack, horizontalRadius, verticalRadius,
                center, use, null);
    }

    /**
     * Inserts into nearby storage discovered from chunk block-entity indexes. The former cubic
     * walk visited every block in a 33x9x33 neighborhood even though only block entities can
     * accept an item; one empty-container return could consequently monopolize the server thread
     * for well over a second in a dense modpack.
     */
    public static boolean insertIntoNearbyStorage(ServerLevel level, VillagerEntityMCA villager,
            ItemStack stack, int horizontalRadius, int verticalRadius, BlockPos center,
            com.aetherianartificer.townstead.storage.StorageUse use,
            @Nullable Predicate<BlockState> stateFilter) {
        if (stack.isEmpty()) return true;
        StorageSearchContext searchContext = new StorageSearchContext(level);
        for (BlockPos pos : nearbyBlockEntities(
                level, villager, center, horizontalRadius, verticalRadius, use)) {

            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            if (stateFilter != null && !stateFilter.test(observed.state())) continue;
            BlockEntity be = observed.blockEntity();
            if (!StorageRoles.isStorageCandidate(level, observed.pos(), be, villager, use)) continue;
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
                        // insertItem must not mutate its input; it returns the remainder. Shrink the
                        // caller's stack in place by what was accepted — reassigning the local would
                        // leave the caller holding the original and duplicate the deposited items.
                        ItemStack remainder = handler.insertItem(i, stack, false);
                        int inserted = beforeCount - remainder.getCount();
                        if (inserted > 0) {
                            stack.shrink(inserted);
                            NearbyStorageIndex.invalidate(level, observed.pos());
                        }
                        if (stack.isEmpty()) return true;
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    private static java.util.List<BlockPos> nearbyBlockEntities(
            ServerLevel level, VillagerEntityMCA villager, BlockPos center,
            int horizontalRadius, int verticalRadius,
            com.aetherianartificer.townstead.storage.StorageUse use) {
        int minX = center.getX() - horizontalRadius;
        int maxX = center.getX() + horizontalRadius;
        int minY = center.getY() - verticalRadius;
        int maxY = center.getY() + verticalRadius;
        int minZ = center.getZ() - horizontalRadius;
        int maxZ = center.getZ() + horizontalRadius;
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        for (int chunkX = SectionPos.blockToSectionCoord(minX);
             chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ);
                 chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++) {
                if (!level.hasChunk(chunkX, chunkZ)) continue;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    if (pos.getX() < minX || pos.getX() > maxX
                            || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    positions.add(pos.immutable());
                }
            }
        }
        positions.sort(java.util.Comparator
                .comparingInt((BlockPos pos) -> StorageRoles.useRank(level.getBlockState(pos), use))
                .thenComparingDouble(pos -> villager.distanceToSqr(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        return positions;
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
        return insertIntoBuildingStorage(level, villager, stack, building,
                com.aetherianartificer.townstead.storage.StorageUse.OUTPUT);
    }

    public static boolean insertIntoBuildingStorage(ServerLevel level, VillagerEntityMCA villager,
            ItemStack stack, net.conczin.mca.server.world.data.Building building,
            com.aetherianartificer.townstead.storage.StorageUse use) {
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
        java.util.List<BlockPos> positions = new java.util.ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!building.containsPos(cursor)) continue;
                    positions.add(cursor.immutable());
                }
            }
        }
        positions.sort(java.util.Comparator
                .comparingInt((BlockPos pos) -> StorageRoles.useRank(level.getBlockState(pos), use))
                .thenComparingDouble(pos -> villager.distanceToSqr(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        for (BlockPos pos : positions) {
            if (stack.isEmpty()) break;
            StorageSearchContext.ObservedBlock observed = searchContext.observe(pos);
            BlockEntity be = observed.blockEntity();
            if (be == null) continue;
            if (!StorageRoles.isStorageCandidate(level, observed.pos(), be, villager, use)) continue;
            if (be instanceof Container container) {
                int beforeCount = stack.getCount();
                insertIntoContainer(container, stack);
                if (stack.getCount() != beforeCount) {
                    NearbyStorageIndex.invalidate(level, observed.pos());
                    com.aetherianartificer.townstead.storage.WorksiteStorageIndex
                            .invalidate(level, observed.pos());
                }
                if (stack.isEmpty()) return true;
                continue;
            }
            IItemHandler handler = searchContext.getItemHandler(observed.pos(), null);
            if (handler != null) {
                for (int i = 0; i < handler.getSlots(); i++) {
                    int beforeCount = stack.getCount();
                    // Shrink in place by what was accepted (see insertIntoNearbyStorage);
                    // reassigning the local would dupe items into handler-only storage.
                    ItemStack remainder = handler.insertItem(i, stack, false);
                    int inserted = beforeCount - remainder.getCount();
                    if (inserted > 0) {
                        stack.shrink(inserted);
                        NearbyStorageIndex.invalidate(level, observed.pos());
                        com.aetherianartificer.townstead.storage.WorksiteStorageIndex
                                .invalidate(level, observed.pos());
                }
                if (stack.isEmpty()) return true;
                continue;
            }
            }
        }
        return stack.isEmpty();
    }

    public static boolean isProcessingContainer(ServerLevel level, BlockPos pos, BlockEntity be) {
        return isProcessingContainer(level.getBlockState(pos), be);
    }

    /**
     * Whether villagers must leave this block alone — a machine rather than a shelf.
     *
     * <p>Decided by data first ({@link StorageRoles}), so supporting a new mod is a tag file
     * and never a code change. The guesses at the bottom are the last word only when nothing
     * has been stated, and either tag overrules them.</p>
     */
    public static boolean isProcessingContainer(BlockState state, BlockEntity be) {
        if (StorageRoles.denied(state)) return true;
        // A block a pack already calls a workstation is a machine; making packs say it twice
        // would just be a second place to forget.
        if (com.aetherianartificer.townstead.work.station.Workstations.byState(state) != null) {
            return true;
        }
        if (StorageRoles.allowed(state)) return false;

        // ── Nothing stated: fall back to guessing, and prefer to skip ──
        if (be instanceof AbstractFurnaceBlockEntity) return true;
        if (state.is(BlockTags.CAMPFIRES)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id == null) return false;
        String path = id.getPath();
        // A name that reads like machinery. Crude and deliberately so — it exists to keep
        // villagers out of an unknown mod's equipment, and any block it catches wrongly is
        // rescued by putting it in #townstead:storage.
        return path.contains("machine") || path.contains("vending")
                || path.contains("terminal") || path.contains("interface")
                || path.contains("trough") || path.contains("feeder")
                || path.contains("feeding") || path.contains("pet_bowl")
                || path.contains("generator") || path.contains("engine")
                || path.contains("press") || path.contains("crusher")
                || path.contains("grinder") || path.contains("centrifuge")
                || path.contains("assembler") || path.contains("processor");
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
