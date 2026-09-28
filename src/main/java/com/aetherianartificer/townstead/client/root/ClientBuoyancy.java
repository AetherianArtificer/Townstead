package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.Fluid;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client-side mirror of {@code Buoyancy}'s fluid lookup, resolved from the synced per-entity
 * expressed-gene set ({@link RootClientStore}) and the gene catalog ({@link RootCatalogClient},
 * which carries each buoyancy gene's nullified fluid ids). Used so the controlling client predicts
 * the same land-movement underwater as the server for its own player.
 */
public final class ClientBuoyancy {

    private ClientBuoyancy() {}

    public static List<TagKey<Fluid>> ignoredFluids(LivingEntity entity) {
        Set<String> geneIds = RootClientStore.behaviorGenes(entity.getId());
        if (geneIds.isEmpty()) return List.of();
        List<TagKey<Fluid>> out = new ArrayList<>();
        for (String geneId : geneIds) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene != null && gene.isBuoyancy()) out.addAll(gene.ignoredFluids());
        }
        return out;
    }
}
