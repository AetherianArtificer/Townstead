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
    private static volatile Map<ResourceLocation, List<ResourceLocation>> COMPANIONS = Map.of();

    private GeneRegistry() {}

    static void replaceAll(Map<ResourceLocation, Gene> next,
                           Map<ResourceLocation, List<ResourceLocation>> companions) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
        COMPANIONS = Map.copyOf(new LinkedHashMap<>(companions));
    }

    @Nullable
    public static Gene byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    /** The companion resource genes a gene declares inline, granted alongside it when expressed. */
    public static List<ResourceLocation> companionsOf(ResourceLocation parentId) {
        return parentId == null ? List.of() : COMPANIONS.getOrDefault(parentId, List.of());
    }

    public static List<Gene> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
