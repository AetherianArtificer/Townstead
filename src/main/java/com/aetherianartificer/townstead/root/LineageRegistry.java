package com.aetherianartificer.townstead.root;

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
        RootRegistry.compositionChanged();
    }

    @Nullable
    public static Lineage byId(ResourceLocation id) {
        if (id == null) return null;
        Lineage direct = ENTRIES.get(id);
        if (direct != null) return direct;
        ResourceLocation legacy = LegacyNamespace.remap(id);
        return legacy == null ? null : ENTRIES.get(legacy);
    }

    public static List<Lineage> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
