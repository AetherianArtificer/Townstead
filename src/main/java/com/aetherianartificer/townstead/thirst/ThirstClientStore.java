package com.aetherianartificer.townstead.thirst;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThirstClientStore {
    private static final Map<Integer, Integer> THIRST_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> QUENCHED_MAP = new ConcurrentHashMap<>();
    private static Runnable onChange;

    private ThirstClientStore() {}

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static void clearOnChange() {
        onChange = null;
    }

    public static void set(int entityId, int thirst, int quenched) {
        THIRST_MAP.put(entityId, thirst);
        QUENCHED_MAP.put(entityId, quenched);
        if (onChange != null) onChange.run();
    }

    public static int getThirst(int entityId) {
        return THIRST_MAP.getOrDefault(entityId, ThirstData.DEFAULT_THIRST);
    }

    public static int getQuenched(int entityId) {
        return QUENCHED_MAP.getOrDefault(entityId, ThirstData.DEFAULT_QUENCHED);
    }

    public static void remove(int entityId) {
        THIRST_MAP.remove(entityId);
        QUENCHED_MAP.remove(entityId);
    }

    public static void clear() {
        THIRST_MAP.clear();
        QUENCHED_MAP.clear();
    }
}
