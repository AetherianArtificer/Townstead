package com.aetherianartificer.townstead.root.appearance;

import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneExpression;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.gene.GeneVariant;
import com.aetherianartificer.townstead.root.gene.types.IrisGeneType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;

/**
 * Writes an {@code editable} iris gene's colours into MCA's own eye colour, so a people's eyes
 * start in their palette and the editor's Eyes tab can still change them. Called when a villager's
 * genes are (re)assigned: founding, a root change, birth.
 */
public final class IrisColors {

    private IrisColors() {}

    /** Seed the villager's eye colour from its expressed editable iris; no-op without one. */
    public static void seed(VillagerEntityMCA villager) {
        if (villager == null || villager.level().isClientSide) return;
        for (Allele allele : GeneExpression.activeAlleles(villager)) {
            Gene gene = GeneRegistry.byId(allele.geneId());
            if (gene == null) continue;
            if (instanceOf(gene, allele) instanceof IrisGeneType.Instance iris && iris.editable()) {
                apply(villager, iris.tint(), iris.tintRight());
                return;
            }
        }
    }

    /** The bearer's expressed editable iris colours ({tint, right or -1}), or null without one. */
    public static int @org.jetbrains.annotations.Nullable [] editable(net.minecraft.world.entity.LivingEntity bearer) {
        return editable(GeneExpression.activeAlleles(bearer));
    }

    /** As {@link #editable(net.minecraft.world.entity.LivingEntity)}, from alleles already expressed. */
    public static int @org.jetbrains.annotations.Nullable [] editable(java.util.List<Allele> expressed) {
        for (Allele allele : expressed) {
            Gene gene = GeneRegistry.byId(allele.geneId());
            if (gene == null) continue;
            if (instanceOf(gene, allele) instanceof IrisGeneType.Instance iris && iris.editable()) {
                return new int[]{iris.tint(), iris.tintRight()};
            }
        }
        return null;
    }

    /** MCA's eye-colour keys in a player's saved data, from {@link #editable}: the people's starting eyes. */
    public static void seedInto(net.minecraft.nbt.CompoundTag entityData, int tint, int right) {
        entityData.putInt("EyeColor", dye(right >= 0 && right != tint ? right : tint));
        entityData.putInt("EyeColorLeft", dye(tint));
    }

    /**
     * Set both eyes to {@code tint}, or, with a {@code right} colour, give each eye its own (MCA
     * draws the left eye from its left dye only with the heterochromia trait). Either side may call
     * this: the editor colours its preview the same way.
     */
    public static void apply(VillagerEntityMCA villager, int tint, int right) {
        if (right >= 0 && right != tint) {
            villager.setEyeDye(dye(right));
            villager.setEyeLeftDye(dye(tint));
            if (!villager.getTraits().hasTrait(Traits.HETEROCHROMIA)) villager.getTraits().addTrait(Traits.HETEROCHROMIA);
        } else {
            villager.setEyeDye(dye(tint));
        }
    }

    /** As {@link #seedInto} writes a player's eyes, onto a preview of that player: no trait is added. */
    public static void applyAsPlayer(VillagerEntityMCA villager, int tint, int right) {
        villager.setEyeDye(dye(right >= 0 && right != tint ? right : tint));
        villager.setEyeLeftDye(dye(tint));
    }

    /** MCA's eye dye is opaque ARGB, with -1 meaning "use the genetic colour". */
    private static int dye(int rgb) {
        int argb = 0xFF000000 | (rgb & 0xFFFFFF);
        return argb == -1 ? 0xFFFEFEFE : argb;
    }

    private static GeneInstance instanceOf(Gene gene, Allele allele) {
        if (!gene.hasVariants()) return gene.instance();
        String variant = AllelePayload.parse(allele.variantId() == null ? "" : allele.variantId()).variant();
        for (GeneVariant v : gene.variants()) {
            if (v.id().equals(variant)) return v.instance();
        }
        return gene.variants().get(0).instance();
    }
}
