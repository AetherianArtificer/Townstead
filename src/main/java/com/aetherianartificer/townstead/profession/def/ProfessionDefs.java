package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side registry of data-driven {@link ProfessionDef}s, replaced each datapack reload by
 * {@link ProfessionDataLoader}. A def's {@code aliases} name other profession ids that mean the
 * same career (the same concept registered by a different mod, or a legacy id); {@link #byId}
 * and {@link #canonicalId} resolve them, so history, progression, and slot policy converge on
 * one career no matter which mod's profession the character holds.
 */
public final class ProfessionDefs {

    private static volatile Map<ResourceLocation, ProfessionDef> ENTRIES = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> ALIASES = Map.of();

    private ProfessionDefs() {}

    public static void replaceAll(Map<ResourceLocation, ProfessionDef> next) {
        Map<ResourceLocation, ProfessionDef> entries = Map.copyOf(new LinkedHashMap<>(next));
        Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
        for (ProfessionDef def : entries.values()) {
            for (ResourceLocation alias : def.aliases()) {
                // A primary id always beats an alias; among aliases, first def wins. The loader
                // reports these collisions as diagnostics; the registry just stays deterministic.
                if (entries.containsKey(alias)) continue;
                aliases.putIfAbsent(alias, def.id());
            }
        }
        ENTRIES = entries;
        ALIASES = Map.copyOf(aliases);
    }

    @Nullable
    public static ProfessionDef byId(ResourceLocation id) {
        if (id == null) return null;
        ProfessionDef direct = ENTRIES.get(id);
        if (direct != null) return direct;
        ResourceLocation canonical = ALIASES.get(id);
        return canonical == null ? null : ENTRIES.get(canonical);
    }

    /** The primary career id for {@code id}: itself, or the def it aliases, or itself unchanged. */
    public static ResourceLocation canonicalId(ResourceLocation id) {
        if (id == null || ENTRIES.containsKey(id)) return id;
        return ALIASES.getOrDefault(id, id);
    }

    public static Map<ResourceLocation, ProfessionDef> all() {
        return ENTRIES;
    }

    public static int size() {
        return ENTRIES.size();
    }
}
