package com.aetherianartificer.townstead.villager;

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

    private TownsteadVillagers() {}

    public static TownsteadVillager get(VillagerEntityMCA villager) {
        TownsteadVillager state = STATES.get(villager.getUUID());
        if (state == null) {
            state = new TownsteadVillager(villager.getUUID());
            TownsteadVillagerState.loadInto(villager, state);
            STATES.put(villager.getUUID(), state);
        }
        if (villager.level() != null) state.touch(villager.level().getGameTime());
        return state;
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

    public static void flushDirtyToEntities(Iterable<? extends VillagerEntityMCA> villagers) {
        for (VillagerEntityMCA villager : villagers) {
            TownsteadVillager state = STATES.get(villager.getUUID());
            if (state == null || !state.isDirty()) continue;
            TownsteadVillagerState.saveSnapshot(villager, state);
            state.clearDirty();
        }
    }

    public static void purgeExpired(long gameTime, long maxIdleTicks) {
        STATES.object2ObjectEntrySet().removeIf(entry ->
                !entry.getValue().isDirty() && gameTime - entry.getValue().lastSeenGameTime() > maxIdleTicks);
    }

    public static void clearAll() {
        STATES.clear();
    }
}
