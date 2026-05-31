package com.aetherianartificer.townstead.origin.trait;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link DataTrait}s, keyed by their MCA
 * trait id, populated by {@code TraitJsonLoader}. The traits themselves live in
 * MCA's {@code Traits} registry; this holds Townstead's effect data for them.
 */
public final class TraitRegistry {

    private static volatile Map<String, DataTrait> ENTRIES = Map.of();

    private TraitRegistry() {}

    static void replaceAll(Map<String, DataTrait> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
        TraitEffects.rebuild(ENTRIES.values());
    }

    @Nullable
    public static DataTrait byId(@Nullable String id) {
        // Map.copyOf's get(null) throws — guard the null id.
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<DataTrait> all() {
        return List.copyOf(ENTRIES.values());
    }
}
