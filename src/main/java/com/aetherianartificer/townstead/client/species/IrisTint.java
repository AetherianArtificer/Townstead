package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** The expressed {@code iris} gene of an entity, read from its synced expressed set. */
public final class IrisTint {
    public record Tint(int rgb, boolean glow) {}

    private IrisTint() {}

    public static @Nullable Tint of(LivingEntity entity) {
        for (String geneId : RootClientStore.expressedGenes(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null) continue;
            if ("iris".equals(gene.faceSlot())) return new Tint(gene.colorFrom(), false);
            if ("iris_glow".equals(gene.faceSlot())) return new Tint(gene.colorFrom(), true);
        }
        return null;
    }
}
