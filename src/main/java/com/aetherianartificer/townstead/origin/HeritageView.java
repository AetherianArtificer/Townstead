package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Allele;
import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.GeneVariant;
import com.aetherianartificer.townstead.origin.gene.Genotype;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side: flattens a villager's realized heritage + diploid genotype into the
 * display-ready {@link HeritageSyncPayload} for the read-only Heritage screen. All
 * names are resolved here (the client's data-pack registries are empty).
 */
public final class HeritageView {

    private HeritageView() {}

    public static HeritageSyncPayload build(VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();

        ResourceLocation originId = ResourceLocation.tryParse(life.originId());
        Origin origin = OriginRegistry.resolveOrDefault(originId);
        Component originNameC = origin != null ? origin.displayName() : Component.empty();
        HeritageRegistry.Resolved race = HeritageRegistry.resolve(life.heritage(), originNameC);

        List<HeritageSyncPayload.AncestryShare> ancestry = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Float> e : life.heritage().fractions().entrySet()) {
            Ancestry a = AncestryRegistry.byId(e.getKey());
            String name = a != null ? a.displayName().getString() : e.getKey().getPath();
            ancestry.add(new HeritageSyncPayload.AncestryShare(name, e.getValue()));
        }

        List<HeritageSyncPayload.GeneRow> rows = new ArrayList<>();
        Genotype genotype = life.genotype();
        for (ResourceLocation locus : genotype.loci()) {
            Allele[] pair = genotype.at(locus);
            if (pair == null) continue;
            Allele a = pair[0];
            Allele b = pair[1];
            Allele expressed = Heredity.express(a, b);
            if (expressed.isWild()) continue; // gene absent on both copies — nothing to show
            Gene gene = geneOf(expressed);
            String label = gene != null ? gene.displayName().getString() : locus.getPath();
            String category = gene != null ? gene.category() : "general";
            // Multi-variant genes show the expressed variant on the right; a plain
            // present gene leaves it empty (its name is the label, no repeat).
            String variant = (gene != null && gene.hasVariants()) ? displayAllele(expressed) : "";
            Allele other = expressed.equals(a) ? b : a;
            String carries;
            if (other.equals(expressed)) {
                carries = "";          // homozygous
            } else if (other.isWild()) {
                carries = "~";         // single-copy carrier (other copy absent)
            } else {
                carries = displayAllele(other);
            }
            String geneId = gene != null ? gene.id().toString() : locus.toString();
            rows.add(new HeritageSyncPayload.GeneRow(geneId, label, category, variant, carries));
        }
        // Group by category so rows read the same way as the Origins genes menu.
        rows.sort(java.util.Comparator.comparing(r -> r.category().toLowerCase(java.util.Locale.ROOT)));

        return new HeritageSyncPayload(villager.getUUID(), race.displayName().getString(),
                originNameC.getString(), ancestry, rows);
    }

    private static Gene geneOf(Allele allele) {
        return allele.isWild() ? null : GeneRegistry.byId(allele.geneId());
    }

    /** The label to show for one allele: its variant name, its gene name, or a dash for wild. */
    private static String displayAllele(Allele allele) {
        if (allele.isWild()) return "—";
        Gene gene = GeneRegistry.byId(allele.geneId());
        if (gene == null) return allele.geneId().getPath();
        if (allele.variantId() != null) {
            for (GeneVariant v : gene.variants()) {
                if (v.id().equals(allele.variantId())) return v.displayName().getString();
            }
        }
        return gene.displayName().getString();
    }
}
