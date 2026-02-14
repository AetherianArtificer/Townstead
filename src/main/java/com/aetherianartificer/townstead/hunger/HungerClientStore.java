package com.aetherianartificer.townstead.hunger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of villager hunger values, populated by S2C packets.
 */
public final class HungerClientStore {

    private static final Map<Integer, Integer> HUNGER_MAP = new ConcurrentHashMap<>();

    private HungerClientStore() {}

    public static void set(int entityId, int hunger) {
        HUNGER_MAP.put(entityId, hunger);
    }

    public static int get(int entityId) {
        return HUNGER_MAP.getOrDefault(entityId, HungerData.DEFAULT_HUNGER);
    }

    public static void remove(int entityId) {
        HUNGER_MAP.remove(entityId);
    }

    public static void clear() {
        HUNGER_MAP.clear();
    }
}
