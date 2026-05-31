package com.aetherianartificer.townstead.origin;

/**
 * One data-pack trait's MCA-registration data, synced to clients so the MCA editor
 * lists data-pack traits on a remote client (whose datapack registries are empty).
 * Effects are server-side only, so they aren't carried here.
 */
public record TraitCatalogEntry(
        String id,
        float chance,
        float inherit,
        boolean usableOnPlayer,
        boolean hidden
) {}
