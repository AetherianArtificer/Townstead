package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;

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
        for (String geneId : RootClientStore.behaviorGenes(entityId)) {
            if (suppresses(geneId, need)) return true;
        }
        RootCatalogEntry origin = RootCatalogClient.origin(RootClientStore.get(entityId));
        if (origin != null) {
            for (RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
                if (suppresses(inherited.geneId(), need)) return true;
            }
        }
        return false;
    }

    /**
     * The icon a need is drawn with for this entity: an expressed diet's own set (blood drops for a
     * vampire) as {@code <prefix>_full/_half/_low.png}, else null for the standard icons.
     */
    public static @org.jetbrains.annotations.Nullable net.minecraft.resources.ResourceLocation icon(
            int entityId, String need, String level) {
        for (String geneId : RootClientStore.behaviorGenes(entityId)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            String prefix = gene == null ? null : gene.needIconPrefix(need);
            if (prefix == null) continue;
            // A data pack's own texture (data/<ns>/textures/...) first, else a resource-pack asset.
            String id = prefix + "_" + level + ".png";
            net.minecraft.resources.ResourceLocation synced =
                    com.aetherianartificer.townstead.client.attachment.AttachmentClient.namedTexture(id);
            return synced != null ? synced : net.minecraft.resources.ResourceLocation.tryParse(id);
        }
        return null;
    }

    private static boolean suppresses(String geneId, String need) {
        GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
        return gene != null && gene.suppressesNeed(need);
    }
}
