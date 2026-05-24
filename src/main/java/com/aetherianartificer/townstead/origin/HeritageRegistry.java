package com.aetherianartificer.townstead.origin;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link Heritage}. Populated by
 * {@link HeritageJsonLoader} on each resource reload. None ship built-in.
 */
public final class HeritageRegistry {
    private static volatile Map<ResourceLocation, Heritage> ENTRIES = Map.of();

    private HeritageRegistry() {}

    static void replaceAll(Map<ResourceLocation, Heritage> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Heritage byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Heritage> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
