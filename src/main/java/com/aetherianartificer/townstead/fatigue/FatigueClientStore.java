package com.aetherianartificer.townstead.fatigue;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FatigueClientStore {
    private static final Map<Integer, Integer> FATIGUE_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> COLLAPSED_MAP = new ConcurrentHashMap<>();
    private static Runnable onChange;

    private FatigueClientStore() {}

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static void clearOnChange() {
        onChange = null;
    }

    public static void set(int entityId, int fatigue, boolean collapsed) {
        FATIGUE_MAP.put(entityId, fatigue);
        COLLAPSED_MAP.put(entityId, collapsed);
        if (onChange != null) onChange.run();
    }

    public static int getFatigue(int entityId) {
        return FATIGUE_MAP.getOrDefault(entityId, FatigueData.DEFAULT_FATIGUE);
    }

    public static boolean hasFatigue(int entityId) {
        return FATIGUE_MAP.containsKey(entityId);
    }

    public static boolean isCollapsed(int entityId) {
        return COLLAPSED_MAP.getOrDefault(entityId, false);
    }

    public static void remove(int entityId) {
        FATIGUE_MAP.remove(entityId);
        COLLAPSED_MAP.remove(entityId);
    }

    public static void clear() {
        FATIGUE_MAP.clear();
        COLLAPSED_MAP.clear();
    }
}
