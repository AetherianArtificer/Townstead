package com.aetherianartificer.townstead.client.catalog;

import java.util.List;

/** Dedicated-client mirror of the server datapack's small decoration catalog. */
public final class DecorationCatalogClientStore {
    private static volatile List<CatalogSyncS2CPayload.DecorationSummary> entries = List.of();

    private DecorationCatalogClientStore() {}

    public static void replaceAll(List<CatalogSyncS2CPayload.DecorationSummary> next) {
        entries = next == null ? List.of() : List.copyOf(next);
    }

    public static List<CatalogSyncS2CPayload.DecorationSummary> entries() {
        return entries;
    }
}
