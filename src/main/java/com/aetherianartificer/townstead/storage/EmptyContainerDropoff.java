package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.items.IItemHandler;
//?} else {
/*import net.minecraftforge.items.IItemHandler;
*///?}

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Returns the empty containers a villager accumulates from consuming food/drink (bowls, glass
 * bottles, buckets) to the storage they came from, so the workers there keep their stock. Empties
 * are carried until the villager is opportunistically near their origin (never a dedicated trip),
 * with graceful fallback to the nearest Farmer's Delight kitchen / any storage when the origin is
 * out of reach for too long, gone, full, or the inventory is filling up.
 */
public final class EmptyContainerDropoff {

    private static final int CADENCE_TICKS = 100;
    // Return a leftover to its origin only if the villager is near it (cheap squared distance, no
    // scan/pathfind). Keeps returns opportunistic — never teleporting empties or making a dedicated
    // trip across a building (e.g. when villagers eat at a table away from where they grabbed food).
    private static final double SOURCE_RETURN_RADIUS_SQ = 16.0 * 16.0;
    // If the villager wanders off and never comes back near the origin, give up after this long and
    // use the nearest storage, so it never hoards empties forever.
    private static final long RETURN_PATIENCE_TICKS = 24000L; // ~1 in-game day held
    // When the inventory is this close to full, stop holding for the origin and dump at the nearest
    // storage so rations/work items aren't blocked.
    private static final int INVENTORY_PRESSURE_FREE_SLOTS = 4;
    private static final int MAX_LEDGER_ORIGINS = 8;

    private record PendingReturn(BlockPos origin, long heldSince) {}
    // villager id -> origin containers it's carrying empties back to. In-memory by design: on
    // reload, held empties simply become origin-less and fall to the nearest-storage path.
    private static final Map<Integer, List<PendingReturn>> LEDGER = new ConcurrentHashMap<>();

    //? if >=1.21 {
    private static final TagKey<Block> FD_KITCHEN_STORAGE_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_UPGRADED_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_upgraded"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_NETHER_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_nether"));
    //?} else {
    /*private static final TagKey<Block> FD_KITCHEN_STORAGE_TAG =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "compat/farmersdelight/kitchen_storage"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_UPGRADED_TAG =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "compat/farmersdelight/kitchen_storage_upgraded"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_NETHER_TAG =
            TagKey.create(Registries.BLOCK, new ResourceLocation("townstead", "compat/farmersdelight/kitchen_storage_nether"));
    *///?}

    private EmptyContainerDropoff() {}

    private static boolean isEmptyContainer(ItemStack stack) {
        return stack.is(Items.GLASS_BOTTLE) || stack.is(Items.BOWL) || stack.is(Items.BUCKET);
    }

    /**
     * Per-villager tick entry, registered independently in the dispatcher (not tied to any need).
     * Runs on a coarse cadence and skips villagers that are actively working so a tool in hand
     * (e.g. a farmer's water bucket) isn't taken mid-task.
     */
    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.tickCount % CADENCE_TICKS != 0) return;
        if (!TownsteadConfig.isEmptyContainerDropoffEnabled()) return;
        long dayTime = level.getDayTime() % 24000L;
        if (villager.getBrain().getSchedule().getActivityAt((int) dayTime) == Activity.WORK) return;
        processReturns(level, villager);
    }

    /**
     * Processes returns on demand, regardless of cadence/activity gate. Called when a villager is
     * already at storage on a supply run, so empties it's been holding while away get returned.
     */
    public static void depositCarried(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.isEmptyContainerDropoffEnabled()) return;
        processReturns(level, villager);
    }

    /** Forgets a villager's pending returns (call when it's removed). */
    public static void forget(VillagerEntityMCA villager) {
        LEDGER.remove(villager.getId());
    }

    /**
     * Tries to put {@code stack} straight back into the {@code source} container right now — only if
     * the villager is near it. Does NOT fall back to other storage (the caller holds + ledgers it
     * instead). Returns true if the whole stack was placed.
     */
    public static boolean tryReturnToSource(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, BlockPos source) {
        if (stack.isEmpty()) return true;
        if (source == null) return false;
        if (villager.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5) > SOURCE_RETURN_RADIUS_SQ) {
            return false;
        }
        insertIntoBlock(new StorageSearchContext(level), source, stack);
        return stack.isEmpty();
    }

    /** Records that {@code villager} is carrying empties owed back to {@code origin}. */
    public static void recordPendingReturn(VillagerEntityMCA villager, BlockPos origin) {
        if (origin == null) return;
        List<PendingReturn> list = LEDGER.computeIfAbsent(villager.getId(), k -> new ArrayList<>());
        for (PendingReturn e : list) {
            if (e.origin().equals(origin)) return; // already tracking this origin
        }
        if (list.size() >= MAX_LEDGER_ORIGINS) list.remove(0); // evict oldest as origin-less
        list.add(new PendingReturn(origin.immutable(), villager.level().getGameTime()));
    }

    /**
     * Opportunistic return pass: hand carried empties back to any origin the villager is currently
     * near (and valid + with room), drop stale/over-patience/pressure leftovers at the nearest
     * storage, and never path anywhere for it. Runs from the idle tick and the supply run.
     */
    private static void processReturns(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        if (!hasAnyEmpty(inv)) { LEDGER.remove(villager.getId()); return; }

        StorageSearchContext context = new StorageSearchContext(level);
        long now = level.getGameTime();
        List<PendingReturn> entries = LEDGER.get(villager.getId());
        if (entries != null) {
            entries.removeIf(e -> {
                boolean inRange = villager.distanceToSqr(
                        e.origin().getX() + 0.5, e.origin().getY() + 0.5, e.origin().getZ() + 0.5) <= SOURCE_RETURN_RADIUS_SQ;
                if (inRange) {
                    depositEmptiesInto(context, inv, e.origin()); // origin gone/full → leftovers stay, freed below
                    return true;
                }
                return now - e.heldSince() >= RETURN_PATIENCE_TICKS; // gave up; freed below
            });
            if (entries.isEmpty()) LEDGER.remove(villager.getId());
        }

        // Fallback to nearest storage when no origin is still being held (all resolved/stale) or the
        // inventory is under pressure. Otherwise keep holding for the remaining origins.
        boolean stillHeld = LEDGER.containsKey(villager.getId());
        boolean pressure = freeSlots(inv) <= INVENTORY_PRESSURE_FREE_SLOTS;
        if (hasAnyEmpty(inv) && (pressure || !stillHeld)) {
            dropOff(level, villager);
        }
        if (!hasAnyEmpty(inv)) LEDGER.remove(villager.getId());
    }

    private static void depositEmptiesInto(StorageSearchContext context, SimpleContainer inv, BlockPos origin) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!isEmptyContainer(stack)) continue;
            insertIntoBlock(context, origin, stack); // shrinks in place; leftover stays if origin full/gone
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }
    }

    private static boolean hasAnyEmpty(SimpleContainer inv) {
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isEmptyContainer(inv.getItem(i))) return true;
        }
        return false;
    }

    private static int freeSlots(SimpleContainer inv) {
        int free = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).isEmpty()) free++;
        }
        return free;
    }

    /**
     * Deposits the villager's empty containers (whole stacks) into nearby storage. One
     * {@link StorageSearchContext} is shared across every container this pass, so only the first
     * deposit pays for the neighborhood scan and the rest hit warm caches. (The non-FD fallback,
     * {@code NearbyItemSources}, still builds its own context internally.)
     */
    private static void dropOff(ServerLevel level, VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        StorageSearchContext context = null;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (!isEmptyContainer(inventory.getItem(i))) continue;
            if (context == null) context = new StorageSearchContext(level);
            ItemStack taken = inventory.removeItemNoUpdate(i);
            int before = taken.getCount();
            if (!store(level, villager, taken, context)) {
                inventory.setItem(i, taken);
                // Nothing fit anywhere; storage is full, so later slots won't fit either.
                if (taken.getCount() == before) break;
            }
        }
    }

    private static boolean store(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, StorageSearchContext context) {
        if (stack.isEmpty()) return true;
        if (ModCompat.isLoaded("farmersdelight")) {
            insertIntoTaggedStorage(villager, stack, context);
            if (stack.isEmpty()) return true;
        }
        return NearbyItemSources.insertIntoNearbyStorage(level, villager, stack, 16, 4);
    }

    /**
     * Deposits a single empty container immediately, trying the {@code source} container it came
     * from first (if recorded, still a container with room, and the villager is still near it), then
     * falling back to FD kitchen / any nearby storage. Mutates {@code stack} in place; returns true
     * if it was fully placed (false means the leftover should be kept on the villager).
     */
    public static boolean deposit(ServerLevel level, VillagerEntityMCA villager, ItemStack stack, @Nullable BlockPos source) {
        if (stack.isEmpty()) return true;
        StorageSearchContext context = new StorageSearchContext(level);
        if (source != null
                && villager.distanceToSqr(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5) <= SOURCE_RETURN_RADIUS_SQ) {
            insertIntoBlock(context, source, stack);
            if (stack.isEmpty()) return true;
        }
        return store(level, villager, stack, context);
    }

    /** Inserts as much of {@code stack} as fits into the single container at {@code pos}, in place. */
    private static void insertIntoBlock(StorageSearchContext context, BlockPos pos, ItemStack stack) {
        StorageSearchContext.ObservedBlock observed = context.observe(pos);
        if (observed.protectedStorage()) return;
        BlockEntity be = observed.blockEntity();
        if (be == null) return;
        if (be instanceof Container container) {
            insertIntoContainer(container, stack);
            if (stack.isEmpty()) return;
        }
        IItemHandler handler = context.getItemHandler(observed.pos(), null);
        if (handler != null) {
            for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
                int before = stack.getCount();
                ItemStack remainder = handler.insertItem(slot, stack, false);
                stack.shrink(before - remainder.getCount());
            }
        }
    }

    /** Inserts as much of {@code stack} as fits into nearby FD kitchen storage, shrinking it in place. */
    private static void insertIntoTaggedStorage(VillagerEntityMCA villager, ItemStack stack, StorageSearchContext context) {
        BlockPos center = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-16, -4, -16),
                center.offset(16, 4, 16))) {
            if (stack.isEmpty()) return;
            StorageSearchContext.ObservedBlock observed = context.observe(pos);
            if (observed.protectedStorage()) continue;
            BlockState state = observed.state();
            if (!(state.is(FD_KITCHEN_STORAGE_TAG) || state.is(FD_KITCHEN_STORAGE_UPGRADED_TAG) || state.is(FD_KITCHEN_STORAGE_NETHER_TAG))) {
                continue;
            }
            BlockEntity be = observed.blockEntity();
            if (be instanceof Container container) {
                insertIntoContainer(container, stack);
                if (stack.isEmpty()) return;
            }
            if (be != null) {
                IItemHandler handler = context.getItemHandler(observed.pos(), null);
                if (handler != null) {
                    for (int slot = 0; slot < handler.getSlots() && !stack.isEmpty(); slot++) {
                        int slotBefore = stack.getCount();
                        ItemStack remainder = handler.insertItem(slot, stack, false);
                        stack.shrink(slotBefore - remainder.getCount());
                    }
                }
            }
        }
    }

    private static void insertIntoContainer(Container container, ItemStack stack) {
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
            slot.grow(move);
            stack.shrink(move);
            container.setChanged();
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty()) continue;
            if (!container.canPlaceItem(i, stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(), stack.getMaxStackSize()));
            //? if >=1.21 {
            container.setItem(i, stack.copyWithCount(move));
            //?} else {
            /*ItemStack portion = stack.copy(); portion.setCount(move); container.setItem(i, portion);
            *///?}
            stack.shrink(move);
            container.setChanged();
        }
    }
}
