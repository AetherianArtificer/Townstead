package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.HeritageSyncPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side store of per-villager heritage snapshots (keyed by villager UUID), fed
 * by {@link HeritageSyncPayload} when the Heritage screen requests one. Read by the
 * Heritage screen.
 */
public final class HeritageClientStore {

    private static final Map<UUID, HeritageSyncPayload> BY_VILLAGER = new ConcurrentHashMap<>();

    private HeritageClientStore() {}

    public static void setFrom(HeritageSyncPayload payload) {
        BY_VILLAGER.put(payload.villager(), payload);
    }

    public static HeritageSyncPayload get(UUID villager) {
        return villager == null ? null : BY_VILLAGER.get(villager);
    }

    public static void clear() {
        BY_VILLAGER.clear();
    }
}
