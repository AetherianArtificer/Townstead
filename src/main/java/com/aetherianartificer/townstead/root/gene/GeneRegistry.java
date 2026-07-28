package com.aetherianartificer.townstead.root.gene;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-side registry of data-pack-loaded {@link Gene}s, populated by
 * {@link GeneJsonLoader} each reload. The lego catalogue races draw from.
 */
public final class GeneRegistry {
    private static volatile Map<ResourceLocation, Gene> ENTRIES = Map.of();
    private static volatile Map<ResourceLocation, List<ResourceLocation>> COMPANIONS = Map.of();
    private static volatile long revision;
    private static volatile Set<ResourceLocation> COMPANION_IDS = Set.of();

    private GeneRegistry() {}

    static void replaceAll(Map<ResourceLocation, Gene> next,
                           Map<ResourceLocation, List<ResourceLocation>> companions) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
        COMPANIONS = Map.copyOf(new LinkedHashMap<>(companions));
        Set<ResourceLocation> flat = new LinkedHashSet<>();
        for (List<ResourceLocation> ids : companions.values()) flat.addAll(ids);
        COMPANION_IDS = Set.copyOf(flat);
        revision++;
        com.aetherianartificer.townstead.pheno.power.Powers.dataReloaded();
    }

    @Nullable
    public static Gene byId(ResourceLocation id) {
        if (id == null) return null;
        Gene direct = ENTRIES.get(id);
        if (direct != null) return direct;
        ResourceLocation legacy = com.aetherianartificer.townstead.root.LegacyNamespace.remap(id);
        return legacy == null ? null : ENTRIES.get(legacy);
    }

    /** The companion resource genes a gene declares inline, granted alongside it when expressed. */
    public static List<ResourceLocation> companionsOf(ResourceLocation parentId) {
        return parentId == null ? List.of() : COMPANIONS.getOrDefault(parentId, List.of());
    }

    /**
     * True when this gene is plumbing declared inside another rather than something a player picks.
     *
     * <p>The reverse of {@link #companionsOf}. A companion rides its parent's expression and is
     * authored without a name or icon of its own, so anything offering the player a list of genes
     * has to be able to leave them out.</p>
     */
    public static boolean isCompanion(ResourceLocation id) {
        if (id == null) return false;
        if (COMPANION_IDS.contains(id)) return true;
        ResourceLocation legacy = com.aetherianartificer.townstead.root.LegacyNamespace.remap(id);
        return legacy != null && COMPANION_IDS.contains(legacy);
    }

    public static List<Gene> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }

    /** Monotonic data-reload generation used by loaded-entity migration caches. */
    public static long revision() {
        return revision;
    }
}
