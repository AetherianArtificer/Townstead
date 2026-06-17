package com.aetherianartificer.townstead.client.skin;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.world.entity.LivingEntity;

import java.util.OptionalInt;

/**
 * Skin tint from a villager's applied origin: finds the origin's COLOR gene
 * ({@code skin_tone}) and returns its tint colour, which the skin-layer mixin
 * MULTIPLIES over MCA's exact melanin×hemoglobin skin — preserving the full vanilla
 * gradient and shifting it toward the race's palette. A white tint is the identity, so
 * Overworlder (white) runs the very same path and renders pixel-exact vanilla; nothing
 * is special-cased. All inputs are server-synced (origin id, the gene's tint in the
 * catalog), so the colour is identical on every client. Villagers with no applied
 * origin (or no colour gene) keep MCA's native skin.
 */
public final class OriginSkinTintProvider implements SkinTintProvider {

    @Override
    public OptionalInt resolve(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId.isEmpty()) return OptionalInt.empty();
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return OptionalInt.empty();
        for (OriginCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(inherited.geneId());
            if (gene != null && gene.isColor()) {
                // Packed tint+mode+strength; the skin mixin and picker unpack it via SkinBlend.
                return OptionalInt.of(SkinBlend.pack(
                        gene.colorFrom(), gene.blendMode(), gene.blendStrength()));
            }
        }
        return OptionalInt.empty();
    }
}
