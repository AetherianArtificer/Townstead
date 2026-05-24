package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.OriginCatalogEntry;

import java.util.List;

/**
 * Client-side copy of the selectable-origin catalog, fed by
 * {@code OriginCatalogSyncPayload}. The picker reads this rather than the
 * server-only {@code OriginRegistry}.
 */
public final class OriginCatalogClient {

    private static volatile List<OriginCatalogEntry> ENTRIES = List.of();

    private OriginCatalogClient() {}

    public static void set(List<OriginCatalogEntry> entries) {
        ENTRIES = entries == null ? List.of() : List.copyOf(entries);
    }

    public static List<OriginCatalogEntry> get() {
        return ENTRIES;
    }

    public static boolean isEmpty() {
        return ENTRIES.isEmpty();
    }
}
