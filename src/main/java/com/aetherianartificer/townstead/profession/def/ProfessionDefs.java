package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-side registry of data-driven {@link ProfessionDef}s, replaced each datapack reload by
 * {@link ProfessionDataLoader}. Compatibility ids resolve either to the Career root or to the
 * root plus a specialization Path. Resolution alone changes Townstead semantics only and never
 * registers or assigns the foreign villager profession. A building provider may separately and
 * explicitly reserve a proprietor seat for that raw id; the employment allocator can then assign
 * it to preserve the provider mod's trades and presentation.
 */
public final class ProfessionDefs {

    private static volatile Map<ResourceLocation, ProfessionDef> ENTRIES = Map.of();
    private static volatile Map<ResourceLocation, Resolution> COMPATIBILITY = Map.of();

    /** The canonical Career meaning of a villager profession, with an optional implied Path. */
    public record Resolution(ResourceLocation professionId, @Nullable String pathId) {}

    private ProfessionDefs() {}

    public static void replaceAll(Map<ResourceLocation, ProfessionDef> next) {
        replaceAll(next, Map.of());
    }

    public static void replaceAll(Map<ResourceLocation, ProfessionDef> next,
                                  Map<ResourceLocation, Resolution> contributions) {
        Map<ResourceLocation, ProfessionDef> entries = Map.copyOf(new LinkedHashMap<>(next));
        Map<ResourceLocation, Resolution> compatibility = new LinkedHashMap<>();
        for (ProfessionDef def : entries.values()) {
            for (ResourceLocation alias : def.aliases()) {
                // A primary id always beats an alias; among aliases, first def wins. The loader
                // reports these collisions as diagnostics; the registry just stays deterministic.
                if (entries.containsKey(alias)) continue;
                compatibility.putIfAbsent(alias, new Resolution(def.id(), null));
            }
        }
        for (Map.Entry<ResourceLocation, Resolution> entry : contributions.entrySet()) {
            if (entries.containsKey(entry.getKey())) continue;
            if (!entries.containsKey(entry.getValue().professionId())) continue;
            compatibility.putIfAbsent(entry.getKey(), entry.getValue());
        }
        ENTRIES = entries;
        COMPATIBILITY = Map.copyOf(compatibility);
    }

    @Nullable
    public static ProfessionDef byId(ResourceLocation id) {
        if (id == null) return null;
        ProfessionDef direct = ENTRIES.get(id);
        if (direct != null) return direct;
        Resolution resolution = COMPATIBILITY.get(id);
        return resolution == null ? null : ENTRIES.get(resolution.professionId());
    }

    /** The primary career id for {@code id}: itself, or the def it aliases, or itself unchanged. */
    public static ResourceLocation canonicalId(ResourceLocation id) {
        if (id == null || ENTRIES.containsKey(id)) return id;
        Resolution resolution = COMPATIBILITY.get(id);
        return resolution == null ? id : resolution.professionId();
    }

    /** The Path implied by a foreign profession id, or null for a root alias/direct Career. */
    @Nullable
    public static String pathId(ResourceLocation id) {
        Resolution resolution = id == null ? null : COMPATIBILITY.get(id);
        return resolution == null ? null : resolution.pathId();
    }

    /** Full semantic resolution; direct Career ids resolve to themselves. */
    @Nullable
    public static Resolution resolve(ResourceLocation id) {
        if (id == null) return null;
        if (ENTRIES.containsKey(id)) return new Resolution(id, null);
        return COMPATIBILITY.get(id);
    }

    /** Every foreign registry id absorbed by this Career, including Path-specific identities. */
    public static Set<ResourceLocation> compatibilityIds(ResourceLocation professionId) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Map.Entry<ResourceLocation, Resolution> entry : COMPATIBILITY.entrySet()) {
            if (entry.getValue().professionId().equals(professionId)) ids.add(entry.getKey());
        }
        return Set.copyOf(ids);
    }

    public static Map<ResourceLocation, Resolution> compatibility() {
        return COMPATIBILITY;
    }

    public static Map<ResourceLocation, ProfessionDef> all() {
        return ENTRIES;
    }

    public static int size() {
        return ENTRIES.size();
    }
}
