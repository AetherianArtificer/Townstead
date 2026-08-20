package com.aetherianartificer.townstead.root.gene;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("townstead/GeneRegistry");
    private static volatile Map<ResourceLocation, Gene> ENTRIES = Map.of();
    /**
     * Unambiguous basename aliases for genes stored in organizational subdirectories. A file at
     * {@code gene/elf/night_vision.json} remains addressable as {@code namespace:night_vision},
     * while its canonical Minecraft resource id is {@code namespace:elf/night_vision}.
     */
    private static volatile Map<ResourceLocation, ResourceLocation> ORGANIZATIONAL_ALIASES = Map.of();
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
        AliasIndex aliases = buildAliases(ENTRIES.keySet(), COMPANION_IDS);
        ORGANIZATIONAL_ALIASES = aliases.aliases();
        for (ResourceLocation id : aliases.ambiguous()) {
            LOGGER.warn("Nested gene short id '{}' is ambiguous; use its folder-qualified id", id);
        }
        revision++;
        com.aetherianartificer.townstead.pheno.power.Powers.dataReloaded();
    }

    @Nullable
    public static Gene byId(ResourceLocation id) {
        ResourceLocation canonical = canonicalId(id);
        return canonical == null ? null : ENTRIES.get(canonical);
    }

    /**
     * Resolve a direct, organizational-short, or legacy id to the gene's canonical resource id.
     * Returns {@code null} when no loaded gene owns the id.
     */
    @Nullable
    public static ResourceLocation canonicalId(ResourceLocation id) {
        if (id == null) return null;
        if (ENTRIES.containsKey(id)) return id;
        ResourceLocation alias = ORGANIZATIONAL_ALIASES.get(id);
        if (alias != null) return alias;
        ResourceLocation legacy = com.aetherianartificer.townstead.root.LegacyNamespace.remap(id);
        if (legacy == null) return null;
        if (ENTRIES.containsKey(legacy)) return legacy;
        return ORGANIZATIONAL_ALIASES.get(legacy);
    }

    /** The companion resource genes a gene declares inline, granted alongside it when expressed. */
    public static List<ResourceLocation> companionsOf(ResourceLocation parentId) {
        ResourceLocation canonical = canonicalId(parentId);
        return canonical == null ? List.of() : COMPANIONS.getOrDefault(canonical, List.of());
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

    /** Build the unambiguous basename aliases; package-private for focused loader tests. */
    static AliasIndex buildAliases(Set<ResourceLocation> ids, Set<ResourceLocation> companionIds) {
        Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
        Set<ResourceLocation> ambiguous = new LinkedHashSet<>();
        for (ResourceLocation id : ids) {
            if (companionIds.contains(id)) continue;
            String path = id.getPath();
            int slash = path.lastIndexOf('/');
            if (slash < 0 || slash == path.length() - 1) continue;
            //? if >=1.21 {
            ResourceLocation shortId = ResourceLocation.fromNamespaceAndPath(
                    id.getNamespace(), path.substring(slash + 1));
            //?} else {
            /*ResourceLocation shortId = new ResourceLocation(
                    id.getNamespace(), path.substring(slash + 1));
            *///?}
            // A real flat resource always wins over a convenience alias.
            if (ids.contains(shortId) || ambiguous.contains(shortId)) continue;
            ResourceLocation previous = aliases.putIfAbsent(shortId, id);
            if (previous != null && !previous.equals(id)) {
                aliases.remove(shortId);
                ambiguous.add(shortId);
            }
        }
        return new AliasIndex(Map.copyOf(aliases), Set.copyOf(ambiguous));
    }

    record AliasIndex(Map<ResourceLocation, ResourceLocation> aliases,
                      Set<ResourceLocation> ambiguous) {}
}
