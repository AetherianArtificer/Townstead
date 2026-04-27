package com.aetherianartificer.townstead.hunger;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
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
    private static final Map<Integer, SyncedHook> HOOK_POSITIONS = new ConcurrentHashMap<>();
    /**
     * Hook ids we've seen exist as a live entity on the client at least once.
     * Used by the renderer to distinguish "link arrived before spawn packet"
     * (don't evict yet — tolerate the race) from "hook was visible and has
     * since been removed from the world" (safe to evict). Without this, the
     * cast-predicate wrapper keeps the rod in cast mode forever because the
     * link entry never gets cleaned up after the reel.
     */
    private static final Set<Integer> CONFIRMED = ConcurrentHashMap.newKeySet();

    private FishermanHookLinkStore() {}

    public static void link(int hookEntityId, int villagerEntityId) {
        if (villagerEntityId < 0) {
            unlink(hookEntityId);
            return;
        }
        HOOK_TO_VILLAGER.entrySet().removeIf(entry ->
                entry.getKey() != hookEntityId && entry.getValue() == villagerEntityId);
        HOOK_POSITIONS.keySet().removeIf(hookId ->
                hookId != hookEntityId && !HOOK_TO_VILLAGER.containsKey(hookId));
        CONFIRMED.removeIf(hookId ->
                hookId != hookEntityId && !HOOK_TO_VILLAGER.containsKey(hookId));
        HOOK_TO_VILLAGER.put(hookEntityId, villagerEntityId);
    }

    public static void link(int hookEntityId, int villagerEntityId, double x, double y, double z) {
        link(hookEntityId, villagerEntityId);
        if (villagerEntityId >= 0) {
            SyncedHook previous = HOOK_POSITIONS.get(hookEntityId);
            long now = System.currentTimeMillis();
            if (previous == null) {
                HOOK_POSITIONS.put(hookEntityId, new SyncedHook(x, y, z, x, y, z, now));
            } else {
                HOOK_POSITIONS.put(hookEntityId,
                        new SyncedHook(previous.x, previous.y, previous.z, x, y, z, now));
            }
        }
    }

    public static void unlink(int hookEntityId) {
        HOOK_TO_VILLAGER.remove(hookEntityId);
        HOOK_POSITIONS.remove(hookEntityId);
        CONFIRMED.remove(hookEntityId);
    }

    public static Integer villagerFor(int hookEntityId) {
        return HOOK_TO_VILLAGER.get(hookEntityId);
    }

    public static Map<Integer, Integer> snapshot() {
        return Collections.unmodifiableMap(HOOK_TO_VILLAGER);
    }

    public static SyncedHook syncedHook(int hookEntityId) {
        return HOOK_POSITIONS.get(hookEntityId);
    }

    /**
     * Called by the renderer after successfully resolving the hook entity as
     * alive — promotes the link from "might be a pre-spawn race" to "we've
     * actually seen this hook." Subsequent null resolves are then safe to
     * evict.
     */
    public static void markConfirmed(int hookEntityId) {
        CONFIRMED.add(hookEntityId);
    }

    public static boolean isConfirmed(int hookEntityId) {
        return CONFIRMED.contains(hookEntityId);
    }

    public static void clear() {
        HOOK_TO_VILLAGER.clear();
        HOOK_POSITIONS.clear();
        CONFIRMED.clear();
    }

    public record SyncedHook(
            double previousX, double previousY, double previousZ,
            double x, double y, double z,
            long updatedAtMillis
    ) {}
}
