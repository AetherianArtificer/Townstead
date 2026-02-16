package com.aetherianartificer.townstead.hunger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of villager hunger values, populated by S2C packets.
 */
public final class HungerClientStore {

    private static final Map<Integer, Integer> HUNGER_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, String> FARM_BLOCKED_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> FARMER_TIER_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> FARMER_XP_MAP = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> FARMER_XP_TO_NEXT_MAP = new ConcurrentHashMap<>();
    private static Runnable onChange;

    private HungerClientStore() {}

    public static void setOnChange(Runnable callback) {
        onChange = callback;
    }

    public static void clearOnChange() {
        onChange = null;
    }

    public static void set(int entityId, int hunger, int farmerTier, int farmerXp, int farmerXpToNext) {
        HUNGER_MAP.put(entityId, hunger);
        FARMER_TIER_MAP.put(entityId, farmerTier);
        FARMER_XP_MAP.put(entityId, farmerXp);
        FARMER_XP_TO_NEXT_MAP.put(entityId, farmerXpToNext);
        if (onChange != null) onChange.run();
    }

    public static int get(int entityId) {
        return HUNGER_MAP.getOrDefault(entityId, HungerData.DEFAULT_HUNGER);
    }

    public static void remove(int entityId) {
        HUNGER_MAP.remove(entityId);
        FARM_BLOCKED_MAP.remove(entityId);
        FARMER_TIER_MAP.remove(entityId);
        FARMER_XP_MAP.remove(entityId);
        FARMER_XP_TO_NEXT_MAP.remove(entityId);
    }

    public static void setFarmBlockedReason(int entityId, String reasonId) {
        FARM_BLOCKED_MAP.put(entityId, reasonId);
        if (onChange != null) onChange.run();
    }

    public static HungerData.FarmBlockedReason getFarmBlockedReason(int entityId) {
        return HungerData.FarmBlockedReason.fromId(FARM_BLOCKED_MAP.getOrDefault(entityId, "none"));
    }

    public static int getFarmerTier(int entityId) {
        return FARMER_TIER_MAP.getOrDefault(entityId, 1);
    }

    public static int getFarmerXp(int entityId) {
        return FARMER_XP_MAP.getOrDefault(entityId, 0);
    }

    public static int getFarmerXpToNext(int entityId) {
        return FARMER_XP_TO_NEXT_MAP.getOrDefault(entityId, 0);
    }

    public static void clear() {
        HUNGER_MAP.clear();
        FARM_BLOCKED_MAP.clear();
        FARMER_TIER_MAP.clear();
        FARMER_XP_MAP.clear();
        FARMER_XP_TO_NEXT_MAP.clear();
    }
}
