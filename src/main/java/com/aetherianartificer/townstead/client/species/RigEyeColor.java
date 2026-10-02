package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-side resolution of a bearer's expressed eye colour: the tint of the
 * carried variant of the origin's {@code eye_color} gene, the same lookup the
 * face overlay uses for the eye sprites — so eye-tinted attachments always
 * match the rendered eyes.
 */
public final class RigEyeColor {

    private RigEyeColor() {}

    /** The entity's eye colour (0xRRGGBB), or {@code -1} when its origin has no eye_color gene. */
    public static int forEntity(LivingEntity entity) {
        RootCatalogEntry origin = RootCatalogClient.origin(RootClientStore.resolve(entity));
        if (origin == null) return -1;
        GeneCatalogEntry colorGene = null;
        for (String geneId : RootClientStore.appearanceGenes(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene != null && gene.isEyeColor()) {
                colorGene = gene;
                break;
            }
        }
        if (colorGene == null || colorGene.variants().isEmpty()) return -1;
        GeneCatalogEntry.Variant variant = null;
        String rolled = RootClientStore.resolveCarriedVariant(entity, colorGene.id());
        if (rolled != null && !rolled.isEmpty()) {
            for (GeneCatalogEntry.Variant v : colorGene.variants()) {
                if (v.id().equals(rolled)) {
                    variant = v;
                    break;
                }
            }
        }
        if (variant == null) {
            // The same stable pre-sync fallback the face overlay uses, so they agree.
            variant = colorGene.variants().get(
                    Math.floorMod(entity.getUUID().hashCode(), colorGene.variants().size()));
        }
        return variant.tint() >= 0 ? variant.tint() : -1;
    }
}
