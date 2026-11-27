package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.tick.EntityLocalCache;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

import java.util.Map;
import java.util.UUID;

/**
 * Runtime owner of typed Townstead villager state.
 */
public final class TownsteadVillagers {
    private static final Object2ObjectOpenHashMap<UUID, TownsteadVillager> STATES = new Object2ObjectOpenHashMap<>();
    // Bumped whenever STATES drops an entry, so a villager's own copy of its entry re-reads the table.
    private static long removals;

    private TownsteadVillagers() {}

    public static TownsteadVillager get(VillagerEntityMCA villager) {
        UUID uuid = villager.getUUID();
        EntityLocalCache local = EntityLocalCache.of(villager);
        TownsteadVillager state = local != null && local.villagerStateEpoch == removals
                && local.villagerStateOwner == uuid ? (TownsteadVillager) local.villagerState : null;
        if (state == null) {
            state = STATES.get(uuid);
            if (state == null) {
                state = new TownsteadVillager(uuid);
                TownsteadVillagerState.loadInto(villager, state);
                STATES.put(uuid, state);
            }
            if (local != null) {
                local.villagerState = state;
                local.villagerStateEpoch = removals;
                local.villagerStateOwner = uuid;
            }
        }
        if (villager.level() != null) state.touch(villager.level().getGameTime());
        return state;
    }

    /** Drops what is held in memory for this villager and reads their state from the entity again. */
    public static TownsteadVillager reload(VillagerEntityMCA villager) {
        STATES.remove(villager.getUUID());
        removals++;
        return get(villager);
    }

    public static void putSnapshot(UUID villagerId, CompoundTag snapshot) {
        TownsteadVillager state = STATES.computeIfAbsent(villagerId, TownsteadVillager::new);
        state.loadSnapshotTag(snapshot);
    }

    public static CompoundTag snapshot(VillagerEntityMCA villager) {
        return get(villager).toSnapshotTag();
    }

    public static int size() {
        return STATES.size();
    }

    public static int dirtyCount() {
        int dirty = 0;
        for (TownsteadVillager state : STATES.values()) {
            if (state.isDirty()) dirty++;
        }
        return dirty;
    }

    /** Flush one villager's state to its persistent snapshot now (e.g. right after an edit). */
    public static void flush(VillagerEntityMCA villager) {
        TownsteadVillager state = STATES.get(villager.getUUID());
        if (state == null) return;
        TownsteadVillagerState.saveSnapshot(villager, state);
        state.clearDirty();
    }

    public static void flushDirtyToEntities(Iterable<? extends VillagerEntityMCA> villagers) {
        for (VillagerEntityMCA villager : villagers) {
            TownsteadVillager state = STATES.get(villager.getUUID());
            if (state == null || !state.isDirty()) continue;
            TownsteadVillagerState.saveSnapshot(villager, state);
            state.clearDirty();
        }
    }

    /** Forgets a villager who will not tick again; the snapshot on the entity stays the record. */
    public static void release(VillagerEntityMCA villager) {
        STATES.remove(villager.getUUID());
        removals++;
    }

    public static void purgeExpired(long gameTime, long maxIdleTicks) {
        if (STATES.object2ObjectEntrySet().removeIf(entry ->
                !entry.getValue().isDirty() && gameTime - entry.getValue().lastSeenGameTime() > maxIdleTicks)) {
            removals++;
        }
    }

    public static void clearAll() {
        STATES.clear();
        removals++;
    }
}
