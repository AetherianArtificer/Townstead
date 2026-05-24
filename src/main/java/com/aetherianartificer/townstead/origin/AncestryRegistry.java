package com.aetherianartificer.townstead.origin;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link Ancestry}. Populated by
 * {@link AncestryJsonLoader} on each resource reload.
 */
public final class AncestryRegistry {
    private static volatile Map<ResourceLocation, Ancestry> ENTRIES = Map.of();

    private AncestryRegistry() {}

    static void replaceAll(Map<ResourceLocation, Ancestry> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Ancestry byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Ancestry> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
