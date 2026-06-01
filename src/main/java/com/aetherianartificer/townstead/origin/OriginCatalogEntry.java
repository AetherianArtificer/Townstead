package com.aetherianartificer.townstead.origin;

import java.util.List;

/**
 * One origin's display data, flattened to plain strings/ids for the picker UI and
 * the catalog sync. Strings are server-resolved (English fallback). Lineage names
 * drive the breadcrumb; {@code inheritedGenes} pair a gene id (looked up in the
 * synced {@link GeneCatalogEntry} dictionary) with its base occurrence;
 * {@code geneRanges} are the MCA float-gene bounds the origin constrains, so the
 * client can roll a WYSIWYG preview before Apply.
 */
public record OriginCatalogEntry(
        String id,
        String name,
        String demonymSingular,
        String demonymPlural,
        String backstory,
        String speciesName,
        String ancestryName,
        String lineageName,
        List<Inherited> inheritedGenes,
        List<GeneRangeView> geneRanges
) {
    /** A gene this origin inherits, with its base occurrence (presence probability). */
    public record Inherited(String geneId, float occurrence) {}

    /** An MCA float-gene range (normalized key + [min,max]) the origin constrains. */
    public record GeneRangeView(String key, float min, float max) {}
}
