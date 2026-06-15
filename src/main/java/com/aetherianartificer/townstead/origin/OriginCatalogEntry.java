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
        List<GeneRangeView> geneRanges,
        // Translate keys paired with the (English) display strings above, so a
        // localized client can re-resolve them. Empty when the source was a
        // literal. The client resolves these at sync-read time into the display
        // fields; on the server they carry the key for the wire. See
        // OriginCatalogSyncPayload.
        String nameKey,
        String demonymSingularKey,
        String demonymPluralKey,
        String backstoryKey,
        String speciesNameKey,
        String ancestryNameKey,
        String lineageNameKey,
        // The species' rig base model reference (e.g. mca:villager, minecraft:skeleton),
        // so the client renderer can swap the rendered model per villager.
        String rigBase,
        // The species' uniform render scale for that rig.
        float rigScale,
        // The species' held-item anchoring (per-hand bone + offset/rotation, or null grips).
        Hold hold,
        // The species' per-state animation sources, so the client rig renderer poses crouch/sleep/fly.
        Animations animations
) {
    /** A gene this origin inherits, with its base occurrence (presence probability). */
    public record Inherited(String geneId, float occurrence) {}

    /** An MCA float-gene range (normalized key + [min,max]) the origin constrains. */
    public record GeneRangeView(String key, float min, float max) {}
}
