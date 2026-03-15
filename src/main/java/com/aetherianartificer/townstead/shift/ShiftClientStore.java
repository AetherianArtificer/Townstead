package com.aetherianartificer.townstead.shift;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for villager shift data, keyed by villager UUID.
 */
public final class ShiftClientStore {
    private static final ConcurrentHashMap<UUID, int[]> SHIFT_MAP = new ConcurrentHashMap<>();

    private ShiftClientStore() {}

    public static void set(UUID villagerUuid, int[] shifts) {
        if (shifts == null || shifts.length != ShiftData.HOURS_PER_DAY) {
            SHIFT_MAP.remove(villagerUuid);
            return;
        }
        SHIFT_MAP.put(villagerUuid, shifts.clone());
    }

    public static int[] get(UUID villagerUuid) {
        int[] cached = SHIFT_MAP.get(villagerUuid);
        if (cached != null) return cached.clone();
        return ShiftData.getVanillaDefault();
    }

    public static boolean has(UUID villagerUuid) {
        return SHIFT_MAP.containsKey(villagerUuid);
    }

    public static void clear() {
        SHIFT_MAP.clear();
    }
}
