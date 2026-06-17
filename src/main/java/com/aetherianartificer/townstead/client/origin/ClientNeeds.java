package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;

import java.util.Set;

/**
 * Client-side mirror of {@code NeedSuppression}: whether a villager's genes switch off a given need,
 * used by the interact-screen status bar to hide that need's icon (matching the server, which pins the
 * need full). Resolved two ways, either is enough: the synced per-entity expressed-gene set, and —
 * more robustly — the villager's origin's inherited genes from the catalog. The origin path mirrors the
 * skin-tint/proportions resolution and does NOT depend on the per-entity expressed-gene packet being
 * fresh (need suppression is a fixed origin grant, so origin-typical equals the individual here), so
 * the icon hides even when that packet is stale or hasn't arrived yet.
 */
public final class ClientNeeds {

    private ClientNeeds() {}

    public static boolean suppresses(int entityId, String need) {
        for (String geneId : OriginClientStore.expressedGenes(entityId)) {
            if (suppresses(geneId, need)) return true;
        }
        OriginCatalogEntry origin = OriginCatalogClient.origin(OriginClientStore.get(entityId));
        if (origin != null) {
            for (OriginCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
                if (suppresses(inherited.geneId(), need)) return true;
            }
        }
        return false;
    }

    private static boolean suppresses(String geneId, String need) {
        GeneCatalogEntry gene = OriginCatalogClient.gene(geneId);
        return gene != null && gene.suppressesNeed(need);
    }
}
