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
        // Bridge data-pack traits into MCA's registry client-side so the editor lists them.
        // (enabledTraits comes from the server via MCA's own config sync.) Defensive against MCA drift.
        boolean any = false;
        for (com.aetherianartificer.townstead.origin.TraitCatalogEntry t : payload.traits()) {
            try {
                net.conczin.mca.entity.ai.Traits.registerTrait(t.id(), t.chance(), t.inherit(), t.usableOnPlayer());
                any = true;
            } catch (Throwable ignored) {
                // Older/newer MCA without this exact signature — skip; trait just won't list.
            }
        }
        if (any) com.aetherianartificer.townstead.origin.trait.TraitJsonLoader.enableRegisteredTraits();
    }

    public static List<OriginCatalogEntry> origins() {
        return ORIGINS;
    }

    /** The catalog entry for an origin id, or {@code null} if unknown. */
    public static OriginCatalogEntry origin(String id) {
        if (id == null || id.isEmpty()) return null;
        for (OriginCatalogEntry e : ORIGINS) {
            if (e.id().equals(id)) return e;
        }
        return null;
    }

    /** Gene display data for a granted-gene id, or {@code null} if unknown. */
    public static GeneCatalogEntry gene(String id) {
        return GENES.get(id);
    }

    public static boolean isEmpty() {
        return ORIGINS.isEmpty();
    }
}
