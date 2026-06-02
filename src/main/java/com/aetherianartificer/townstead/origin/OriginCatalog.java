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
            Component nameC = origin.displayName();
            String name = nameC.getString();
            Demonym demonym = OriginRegistry.resolveDemonym(origin);
            Component singC = demonym != null ? demonym.singular() : nameC;
            Component plurC = demonym != null ? demonym.plural() : nameC;
            String singular = singC.getString();
            String plural = plurC.getString();
            Component backstory = OriginRegistry.resolveBackstory(origin);
            Species spc = SpeciesRegistry.byId(origin.species());
            Ancestry anc = AncestryRegistry.byId(origin.ancestry());
            Lineage lin = LineageRegistry.byId(origin.lineage());

            List<InheritedGene> inherited = OriginRegistry.effectiveInheritedGenes(origin.id());
            List<OriginCatalogEntry.Inherited> views = new ArrayList<>(inherited.size());
            for (InheritedGene gene : inherited) {
                Gene def = GeneRegistry.byId(gene.geneId());
                // Body metrics render as ranges in the Body tab, not as viewer chips.
                if (def != null && def.instance() instanceof
                        com.aetherianartificer.townstead.origin.gene.types.BodyMetricGeneType.Instance) {
                    continue;
                }
                views.add(new OriginCatalogEntry.Inherited(gene.geneId().toString(), gene.occurrence()));
                genes.computeIfAbsent(gene.geneId(), OriginCatalog::toGeneEntry);
            }

            Map<String, GeneRange> metrics = OriginGenes.resolveBodyMetrics(inherited);
            List<OriginCatalogEntry.GeneRangeView> ranges = new ArrayList<>(metrics.size());
            for (Map.Entry<String, GeneRange> r : metrics.entrySet()) {
                ranges.add(new OriginCatalogEntry.GeneRangeView(r.getKey(), r.getValue().min(), r.getValue().max()));
            }

            origins.add(new OriginCatalogEntry(
                    origin.id().toString(), name, singular, plural,
                    backstory != null ? backstory.getString() : "",
                    name(spc), name(anc), name(lin),
                    views, ranges,
                    keyOf(nameC), keyOf(singC), keyOf(plurC), keyOf(backstory),
                    keyOf(spc != null ? spc.displayName() : null),
                    keyOf(anc != null ? anc.displayName() : null),
                    keyOf(lin != null ? lin.displayName() : null)));
        }
        return new Snapshot(origins, new ArrayList<>(genes.values()), traits);
    }

    private static GeneCatalogEntry toGeneEntry(ResourceLocation geneId) {
        Gene gene = GeneRegistry.byId(geneId);
        if (gene == null) {
            return new GeneCatalogEntry(geneId.toString(), geneId.getPath(), "", "general",
                    GeneDisplay.Kind.BOOLEAN.ordinal(), 0f, 1f, "", 0f, 0, "", 1, List.of(), "", "");
        }
        GeneDisplay display = gene.display();
        List<GeneCatalogEntry.Variant> variants = new ArrayList<>();
        if (gene.hasVariants()) {
            for (com.aetherianartificer.townstead.origin.gene.GeneVariant v : gene.variants()) {
                variants.add(new GeneCatalogEntry.Variant(
                        v.id(), v.displayName().getString(), v.weight(), keyOf(v.displayName())));
            }
        }
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
                gene.weight(),
                variants,
                keyOf(gene.displayName()),
                keyOf(gene.description()));
    }

    /** Translate key of a Component when it is a {@code translatable}, else "" (literal). */
    private static String keyOf(Component c) {
        if (c != null && c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
            return tc.getKey();
        }
        return "";
    }

    private static String name(Species species) {
        return species != null ? species.displayName().getString() : "";
    }

    private static String name(Ancestry ancestry) {
        return ancestry != null ? ancestry.displayName().getString() : "";
    }

    private static String name(Lineage lineage) {
        return lineage != null ? lineage.displayName().getString() : "";
    }
}
