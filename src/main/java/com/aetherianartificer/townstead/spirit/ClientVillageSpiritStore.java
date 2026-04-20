package com.aetherianartificer.townstead.spirit;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Client-only store for the server's synced {@link VillageSpiritSyncPayload}.
 * The blueprint Spirit page reads directly from here. Keyed by village id,
 * not coordinates — the client doesn't know which village is "nearest" but
 * the blueprint UI already has a selected village context, so we can query
 * by that id.
 */
public final class ClientVillageSpiritStore {
    private static final ConcurrentMap<Integer, VillageSpiritSyncPayload> STATE = new ConcurrentHashMap<>();

    private ClientVillageSpiritStore() {}

    public static void put(VillageSpiritSyncPayload payload) {
        if (payload == null) return;
        STATE.put(payload.villageId(), payload);
    }

    public static Optional<VillageSpiritSyncPayload> get(int villageId) {
        return Optional.ofNullable(STATE.get(villageId));
    }

    public static void clear() {
        STATE.clear();
    }
}
