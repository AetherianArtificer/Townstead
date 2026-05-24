package com.aetherianartificer.townstead.origin.gene;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link Gene}s, populated by
 * {@link GeneJsonLoader} each reload. The lego catalogue races draw from.
 */
public final class GeneRegistry {
    private static volatile Map<ResourceLocation, Gene> ENTRIES = Map.of();

    private GeneRegistry() {}

    static void replaceAll(Map<ResourceLocation, Gene> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static Gene byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<Gene> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
