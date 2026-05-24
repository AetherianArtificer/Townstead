package com.aetherianartificer.townstead.origin;

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
    }

    @Nullable
    public static Species byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Species> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
