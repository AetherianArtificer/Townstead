package com.aetherianartificer.townstead.client.animation.emote;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mapping of entity UUID → active {@link EmotePlayback}. Concurrent
 * because triggers may arrive on the network thread while reads happen on the render
 * thread.
 */
public final class EmotePlaybackRegistry {
    private static final ConcurrentHashMap<UUID, EmotePlayback> ACTIVE = new ConcurrentHashMap<>();

    private EmotePlaybackRegistry() {}

    public static void put(UUID entityUuid, EmotePlayback playback) {
        ACTIVE.put(entityUuid, playback);
    }

    public static EmotePlayback get(UUID entityUuid) {
        return ACTIVE.get(entityUuid);
    }

    public static void remove(UUID entityUuid) {
        ACTIVE.remove(entityUuid);
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
