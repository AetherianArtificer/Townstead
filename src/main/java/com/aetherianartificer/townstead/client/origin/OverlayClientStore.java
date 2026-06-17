package com.aetherianartificer.townstead.client.origin;

import java.util.List;

/**
 * Holds the local player's currently-active {@code overlay} gene ids (fed by
 * {@code OverlayActiveS2CPayload}); read by {@link OverlayHudOverlay}. Cleared on
 * logout (see {@code Townstead}).
 */
public final class OverlayClientStore {

    private static volatile List<String> ACTIVE = List.of();

    private OverlayClientStore() {}

    public static void set(List<String> geneIds) {
        ACTIVE = geneIds == null ? List.of() : List.copyOf(geneIds);
    }

    public static List<String> get() {
        return ACTIVE;
    }

    public static void clear() {
        ACTIVE = List.of();
    }
}
