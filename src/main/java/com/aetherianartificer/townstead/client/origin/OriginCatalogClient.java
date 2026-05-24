package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogSyncPayload;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side copy of the selectable-origin catalog + gene dictionary, fed by
 * {@code OriginCatalogSyncPayload}. The picker reads this rather than the
 * server-only registries.
 */
public final class OriginCatalogClient {

    private static volatile List<OriginCatalogEntry> ORIGINS = List.of();
    private static volatile Map<String, GeneCatalogEntry> GENES = Map.of();

    private OriginCatalogClient() {}

    public static void setFrom(OriginCatalogSyncPayload payload) {
        ORIGINS = List.copyOf(payload.entries());
        Map<String, GeneCatalogEntry> genes = new LinkedHashMap<>();
        for (GeneCatalogEntry g : payload.genes()) {
            genes.put(g.id(), g);
        }
        GENES = Map.copyOf(genes);
    }

    public static List<OriginCatalogEntry> origins() {
        return ORIGINS;
    }

    /** Gene display data for a granted-gene id, or {@code null} if unknown. */
    public static GeneCatalogEntry gene(String id) {
        return GENES.get(id);
    }

    public static boolean isEmpty() {
        return ORIGINS.isEmpty();
    }
}
