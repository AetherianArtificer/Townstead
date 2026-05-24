package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.OriginSetC2SPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side current-origin cache, keyed by network entity id (or
 * {@link OriginSetC2SPayload#SELF} for the player's own origin). Fed by
 * {@code OriginSyncS2CPayload}; read live by the picker to highlight the current
 * row. Cleared on logout.
 */
public final class OriginClientStore {

    private static final Map<Integer, String> BY_ENTITY = new ConcurrentHashMap<>();

    private OriginClientStore() {}

    public static void set(int entityId, String originId) {
        BY_ENTITY.put(entityId, originId == null ? "" : originId);
    }

    /** Current origin id for the target, or empty string if unknown. */
    public static String get(int entityId) {
        return BY_ENTITY.getOrDefault(entityId, "");
    }

    public static String getSelf() {
        return get(OriginSetC2SPayload.SELF);
    }

    public static void clear() {
        BY_ENTITY.clear();
    }
}
