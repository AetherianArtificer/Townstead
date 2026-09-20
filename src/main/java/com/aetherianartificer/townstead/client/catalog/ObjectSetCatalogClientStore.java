package com.aetherianartificer.townstead.client.catalog;

import java.util.List;

/** Dedicated-client mirror of the server datapack's small object-set catalog. */
public final class ObjectSetCatalogClientStore {
    private static volatile List<CatalogSyncS2CPayload.ObjectSetSummary> entries = List.of();

    private ObjectSetCatalogClientStore() {}

    public static void replaceAll(List<CatalogSyncS2CPayload.ObjectSetSummary> next) {
        entries = next == null ? List.of() : List.copyOf(next);
    }

    public static List<CatalogSyncS2CPayload.ObjectSetSummary> entries() {
        return entries;
    }
}
