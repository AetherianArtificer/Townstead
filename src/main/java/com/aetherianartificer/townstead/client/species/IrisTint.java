package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * The expressed {@code iris} gene of an entity, read from its synced expressed set. A variants iris
 * gene gives the entity's carried variant (a stable pick by UUID until its allele syncs), whose
 * colour may differ per eye.
 */
public final class IrisTint {
    /**
     * {@code rgb} colours the left eye, {@code rgbRight} the right (the same unless mismatched).
     * {@code override} is false for an editable iris, whose colour already lives in MCA's own eye
     * colour: only its glow applies.
     */
    public record Tint(int rgb, int rgbRight, boolean glow, boolean override) {
        public Tint(int rgb, boolean glow) {
            this(rgb, rgb, glow, true);
        }

        public int rgb(boolean left) {
            return left ? rgb : rgbRight;
        }
    }

    private IrisTint() {}

    public static @Nullable Tint of(LivingEntity entity) {
        for (String geneId : RootClientStore.expressedGenes(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null) continue;
            boolean iris = "iris".equals(gene.faceSlot()), irisGlow = "iris_glow".equals(gene.faceSlot());
            boolean seed = "iris_seed".equals(gene.faceSlot());
            if (!iris && !irisGlow && !seed) continue;
            GeneCatalogEntry.Variant variant = carried(entity, gene);
            if (seed) {
                boolean glow = variant != null && variant.glow();
                if (glow) return new Tint(0, 0, true, false);
                continue;
            }
            if (variant != null && variant.tint() >= 0) {
                int right = variant.palette().isEmpty() ? variant.tint() : variant.palette().get(0);
                return new Tint(variant.tint(), right, variant.glow() || irisGlow, true);
            }
            return new Tint(gene.colorFrom(), irisGlow);
        }
        return null;
    }

    /** The entity's carried variant of a variants gene, else a stable pick by UUID; null without variants. */
    private static @Nullable GeneCatalogEntry.Variant carried(LivingEntity entity, GeneCatalogEntry gene) {
        if (gene.variants().isEmpty()) return null;
        String rolled = RootClientStore.resolveCarriedVariant(entity, gene.id());
        if (rolled != null && !rolled.isEmpty()) {
            for (GeneCatalogEntry.Variant v : gene.variants()) {
                if (v.id().equals(rolled)) return v;
            }
        }
        java.util.List<GeneCatalogEntry.Variant> pool = new java.util.ArrayList<>();
        for (GeneCatalogEntry.Variant v : gene.variants()) {
            for (int i = 0; i < Math.max(1, v.weight()); i++) pool.add(v);
        }
        return pool.get(Math.floorMod(entity.getUUID().hashCode(), pool.size()));
    }
}
