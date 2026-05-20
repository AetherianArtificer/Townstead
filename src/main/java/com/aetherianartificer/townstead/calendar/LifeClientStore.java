package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of villager birth snapshots keyed by entity id. Cleared
 * on logout. Mirrors {@link com.aetherianartificer.townstead.hunger.HungerClientStore}
 * in shape.
 */
public final class LifeClientStore {

    public record Snapshot(
            int birthYear,
            int birthMonthIndex,
            int birthDayOfMonth,
            String birthMonthKey,
            String birthMonthFallback,
            int ageYears,
            boolean stamped
    ) {
        public Component birthMonthComponent() {
            return ComponentSync.reconstruct(birthMonthKey, birthMonthFallback);
        }
    }

    private static final Map<Integer, Snapshot> BY_ENTITY = new ConcurrentHashMap<>();

    private LifeClientStore() {}

    public static void setFrom(VillagerLifeSyncPayload payload) {
        BY_ENTITY.put(payload.entityId(), new Snapshot(
                payload.birthYear(),
                payload.birthMonthIndex(),
                payload.birthDayOfMonth(),
                payload.birthMonthKey(),
                payload.birthMonthFallback(),
                payload.ageYears(),
                payload.stamped()
        ));
    }

    @Nullable
    public static Snapshot get(int entityId) {
        return BY_ENTITY.get(entityId);
    }

    public static void remove(int entityId) {
        BY_ENTITY.remove(entityId);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }
}
