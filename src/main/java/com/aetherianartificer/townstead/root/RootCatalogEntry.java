package com.aetherianartificer.townstead.root;

import java.util.List;

/**
 * One origin's display data, flattened to plain strings/ids for the picker UI and
 * the catalog sync. Strings are server-resolved (English fallback). Lineage names
 * drive the breadcrumb; {@code inheritedGenes} pair a gene id (looked up in the
 * synced {@link GeneCatalogEntry} dictionary) with its base occurrence;
 * {@code geneRanges} are the MCA float-gene bounds the origin constrains, so the
 * client can roll a WYSIWYG preview before Apply.
 */
public record RootCatalogEntry(
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
        // RootCatalogSyncPayload.
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
        // The species' per-state animation sources, so the client rig renderer poses crouch/sleep/fly.
        Animations animations,
        // Whether this species' body shows breasts (false hides MCA's breast part for it).
        boolean breasts,
        // Effective founder policy for MCA's native hair layer. Realized heritage may override it.
        boolean hair,
        List<com.aetherianartificer.townstead.root.appearance.HairColorRange> hairColorRanges,
        List<com.aetherianartificer.townstead.root.appearance.HairColorChoice> hairColors,
        List<com.aetherianartificer.townstead.root.appearance.HairGradient> hairGradients,
        // Per-life-stage rig override, one entry per stage of this origin's effective life cycle (empty
        // string = species rig). Empty list when no stage overrides the rig. Lets a stage (e.g. "egg")
        // render a different model; the client renderer indexes it by the villager's current stage.
        List<String> stageRigs,
        // Data-pack Character-editor layout from the species (null = keep MCA's full native tab).
        CharacterEditorLayout characterEditor,
        // Server-config blocklisted: hidden from picker lists and rejected on apply, but kept in the
        // catalog so entities that already carry this root still resolve their rig/labels client-side.
        boolean blocked
) {
    /** A gene this origin inherits, with its base occurrence (presence probability). */
    public record Inherited(String geneId, float occurrence) {}

    /** An MCA float-gene range (normalized key + [min,max]) the origin constrains. */
    public record GeneRangeView(String key, float min, float max) {}
}
