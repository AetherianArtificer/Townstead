package com.aetherianartificer.townstead.shift;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for villager shift data, keyed by villager UUID. Holds the
 * daily 24-slot array plus, separately, the weekly schedule state (mode +
 * per-weekday template references).
 */
public final class ShiftClientStore {
    private static final ConcurrentHashMap<UUID, int[]> SHIFT_MAP = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, WeekState> WEEK_MAP = new ConcurrentHashMap<>();

    /** Per-villager weekly state mirror. */
    public record WeekState(String mode, List<String> weekDays) {
        public WeekState {
            mode = ShiftData.MODE_WEEKLY.equals(mode) ? ShiftData.MODE_WEEKLY : ShiftData.MODE_DAILY;
            weekDays = weekDays == null ? List.of() : List.copyOf(weekDays);
        }
        public boolean isWeekly() { return ShiftData.MODE_WEEKLY.equals(mode); }
        public String dayTemplate(int dayOfWeek) {
            if (dayOfWeek < 0 || dayOfWeek >= weekDays.size()) return "";
            return weekDays.get(dayOfWeek);
        }
    }

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

    // ---- Weekly state ----

    public static void setWeek(UUID villagerUuid, String mode, List<String> weekDays) {
        if (villagerUuid == null) return;
        WEEK_MAP.put(villagerUuid, new WeekState(mode, weekDays == null ? new ArrayList<>() : weekDays));
    }

    /** Never null; defaults to a daily/empty state for villagers not yet synced. */
    public static WeekState getWeek(UUID villagerUuid) {
        WeekState s = WEEK_MAP.get(villagerUuid);
        return s != null ? s : new WeekState(ShiftData.MODE_DAILY, List.of());
    }

    public static boolean isWeekly(UUID villagerUuid) {
        WeekState s = WEEK_MAP.get(villagerUuid);
        return s != null && s.isWeekly();
    }

    public static void clear() {
        SHIFT_MAP.clear();
        WEEK_MAP.clear();
    }
}
