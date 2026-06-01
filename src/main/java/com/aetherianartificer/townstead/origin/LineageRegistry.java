package com.aetherianartificer.townstead.origin;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link Lineage}. Populated by
 * {@link LineageJsonLoader} on each resource reload. None ship built-in.
 */
public final class LineageRegistry {
    private static volatile Map<ResourceLocation, Lineage> ENTRIES = Map.of();

    private LineageRegistry() {}

    static void replaceAll(Map<ResourceLocation, Lineage> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Lineage byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Lineage> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
