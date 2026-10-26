package com.aetherianartificer.townstead.root;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link Species}. Populated by
 * {@link SpeciesJsonLoader} on each resource reload; reads are unsynchronized
 * against the atomically rebuilt map (see {@code CalendarProfileRegistry}).
 */
public final class SpeciesRegistry {
    private static volatile Map<ResourceLocation, Species> ENTRIES = Map.of();

    private SpeciesRegistry() {}

    static void replaceAll(Map<ResourceLocation, Species> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
        RootRegistry.compositionChanged();
    }

    @Nullable
    public static Species byId(ResourceLocation id) {
        if (id == null) return null;
        Species direct = ENTRIES.get(id);
        if (direct != null) return direct;
        ResourceLocation legacy = LegacyNamespace.remap(id);
        return legacy == null ? null : ENTRIES.get(legacy);
    }

    /**
     * The id a declared species reference actually resolves to: the registered entry's own id when
     * one resolves (folding legacy-namespace references onto the entry they remap to), else the
     * legacy remap of the reference, else the reference as given. Callers that compare species ids
     * must compare canonical ids, or {@code townstead_origins:} and {@code townstead_roots:}
     * references to the same species read as different species.
     */
    @Nullable
    public static ResourceLocation canonicalId(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Species resolved = byId(id);
        return resolved != null ? resolved.id() : LegacyNamespace.canonical(id);
    }

    public static List<Species> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
