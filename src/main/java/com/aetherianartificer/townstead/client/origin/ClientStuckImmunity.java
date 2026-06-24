package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Client-side mirror of {@code StuckImmunity}, resolved from the synced per-entity expressed-gene set
 * ({@link OriginClientStore}) and the gene catalog ({@link OriginCatalogClient}, which carries each
 * stuck-immunity gene's block ids). Used so the controlling client predicts the same result as the
 * server for a player walking through cobwebs (the slowdown is computed in local physics, so a
 * server-only decision would be overridden and the player would still stick).
 */
public final class ClientStuckImmunity {

    private ClientStuckImmunity() {}

    public static boolean covers(LivingEntity entity, BlockState state) {
        Set<String> geneIds = OriginClientStore.expressedGenes(entity.getId());
        if (geneIds.isEmpty()) return false;
        for (String geneId : geneIds) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(geneId);
            if (gene == null || !gene.isStuckImmunity()) continue;
            for (ResourceLocation blockId : gene.stuckBlocks()) {
                if (state.is(BuiltInRegistries.BLOCK.get(blockId))) return true;
            }
        }
        return false;
    }
}
