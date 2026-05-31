package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side: flattens the loaded origin + gene registries into wire/UI data so
 * a remote client (whose datapack-driven registries are empty) can render and
 * label origins and their inherited genes. Builds an origin list plus a gene
 * dictionary covering only the genes some origin actually inherits.
 */
public final class OriginCatalog {

    private OriginCatalog() {}

    public record Snapshot(List<OriginCatalogEntry> origins, List<GeneCatalogEntry> genes,
                           List<TraitCatalogEntry> traits) {}

    public static Snapshot build() {
        List<OriginCatalogEntry> origins = new ArrayList<>();
        Map<ResourceLocation, GeneCatalogEntry> genes = new LinkedHashMap<>();
        List<TraitCatalogEntry> traits = new ArrayList<>();
        for (com.aetherianartificer.townstead.origin.trait.DataTrait t
                : com.aetherianartificer.townstead.origin.trait.TraitRegistry.all()) {
            traits.add(new TraitCatalogEntry(t.id(), t.chance(), t.inherit(), t.usableOnPlayer(), t.hidden()));
        }

        for (Origin origin : OriginRegistry.all()) {
            String name = origin.displayName().getString();
            Demonym demonym = OriginRegistry.resolveDemonym(origin);
            String singular = demonym != null ? demonym.singular().getString() : name;
            String plural = demonym != null ? demonym.plural().getString() : name;
            Component backstory = OriginRegistry.resolveBackstory(origin);

            Genome genome = OriginRegistry.effectiveGenome(origin);
            List<InheritedGene> inherited = genome.inheritedGenes();
            List<OriginCatalogEntry.Inherited> views = new ArrayList<>(inherited.size());
            for (InheritedGene gene : inherited) {
                views.add(new OriginCatalogEntry.Inherited(gene.geneId().toString(), gene.occurrence()));
                genes.computeIfAbsent(gene.geneId(), OriginCatalog::toGeneEntry);
            }

            List<OriginCatalogEntry.GeneRangeView> ranges = new ArrayList<>(genome.genes().size());
            for (Map.Entry<String, GeneRange> r : genome.genes().entrySet()) {
                ranges.add(new OriginCatalogEntry.GeneRangeView(r.getKey(), r.getValue().min(), r.getValue().max()));
            }

            origins.add(new OriginCatalogEntry(
                    origin.id().toString(), name, singular, plural,
                    backstory != null ? backstory.getString() : "",
                    name(SpeciesRegistry.byId(origin.species())),
                    name(AncestryRegistry.byId(origin.ancestry())),
                    name(HeritageRegistry.byId(origin.heritage())),
                    views, ranges));
        }
        return new Snapshot(origins, new ArrayList<>(genes.values()), traits);
    }

    private static GeneCatalogEntry toGeneEntry(ResourceLocation geneId) {
        Gene gene = GeneRegistry.byId(geneId);
        if (gene == null) {
            return new GeneCatalogEntry(geneId.toString(), geneId.getPath(), "", "general",
                    GeneDisplay.Kind.BOOLEAN.ordinal(), 0f, 1f, "", 0f, 0, "", 1);
        }
        GeneDisplay display = gene.display();
        return new GeneCatalogEntry(
                geneId.toString(),
                gene.displayName().getString(),
                gene.description() != null ? gene.description().getString() : "",
                gene.category(),
                display.kind().ordinal(),
                display.min(), display.max(),
                display.targetId(), display.amount(),
                gene.dominance().ordinal(),
                gene.locus() != null ? gene.locus().toString() : "",
                gene.weight());
    }

    private static String name(Species species) {
        return species != null ? species.displayName().getString() : "";
    }

    private static String name(Ancestry ancestry) {
        return ancestry != null ? ancestry.displayName().getString() : "";
    }

    private static String name(Heritage heritage) {
        return heritage != null ? heritage.displayName().getString() : "";
    }
}
