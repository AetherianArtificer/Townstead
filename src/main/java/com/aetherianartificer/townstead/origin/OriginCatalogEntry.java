package com.aetherianartificer.townstead.origin;

/**
 * One origin's display data, flattened to plain strings for the picker UI and
 * the catalog sync. Strings are server-resolved (English fallback) — locale-aware
 * origin UI text is a later enhancement.
 */
public record OriginCatalogEntry(
        String id,
        String name,
        String demonymSingular,
        String demonymPlural,
        String backstory
) {
}
