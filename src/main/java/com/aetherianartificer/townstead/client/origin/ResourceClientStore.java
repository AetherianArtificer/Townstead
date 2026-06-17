package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.ability.ResourceSyncS2CPayload;

import java.util.List;

/**
 * Client-side cache of the local player's resource meters, fed by
 * {@code ResourceSyncS2CPayload} and read by the HUD overlay. Cleared on logout.
 */
public final class ResourceClientStore {

    private static volatile List<ResourceSyncS2CPayload.Bar> bars = List.of();

    private ResourceClientStore() {}

    public static void set(List<ResourceSyncS2CPayload.Bar> value) {
        bars = value == null ? List.of() : List.copyOf(value);
    }

    public static List<ResourceSyncS2CPayload.Bar> get() {
        return bars;
    }

    public static void clear() {
        bars = List.of();
    }
}
