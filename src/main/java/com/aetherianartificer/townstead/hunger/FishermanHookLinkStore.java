package com.aetherianartificer.townstead.hunger;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side map from FishingHook entity id → villager entity id, populated by
 * FishermanHookLinkPayload. FishermanLineRenderer reads this each frame to know
 * which villager should be the line's origin for each active hook.
 *
 * Entries are not explicitly cleaned up on hook despawn: the renderer simply
 * skips ids that no longer resolve to a live entity, and the next hook takes
 * a new id. On disconnect, clear() wipes the whole map.
 */
public final class FishermanHookLinkStore {
    private static final Map<Integer, Integer> HOOK_TO_VILLAGER = new ConcurrentHashMap<>();

    private FishermanHookLinkStore() {}

    public static void link(int hookEntityId, int villagerEntityId) {
        if (villagerEntityId < 0) {
            HOOK_TO_VILLAGER.remove(hookEntityId);
            return;
        }
        HOOK_TO_VILLAGER.put(hookEntityId, villagerEntityId);
    }

    public static void unlink(int hookEntityId) {
        HOOK_TO_VILLAGER.remove(hookEntityId);
    }

    public static Integer villagerFor(int hookEntityId) {
        return HOOK_TO_VILLAGER.get(hookEntityId);
    }

    public static Map<Integer, Integer> snapshot() {
        return Collections.unmodifiableMap(HOOK_TO_VILLAGER);
    }

    public static void clear() {
        HOOK_TO_VILLAGER.clear();
    }
}
