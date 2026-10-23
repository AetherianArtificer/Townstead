package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-driven {@link SkillDef}s, replaced each datapack reload by
 * {@link ProfessionDataLoader}. Skills that moved into per-profession directories gained
 * path-scoped ids ({@code townstead:cook/open_flame}); the legacy index resolves the old flat
 * form ({@code townstead:open_flame}) whenever exactly one skill's last path segment matches, so
 * learned history saved under old ids keeps resolving.
 */
public final class SkillDefs {

    private static volatile Map<ResourceLocation, SkillDef> ENTRIES = Map.of();
    private static volatile Map<ResourceLocation, ResourceLocation> LEGACY = Map.of();

    private SkillDefs() {}

    public static void replaceAll(Map<ResourceLocation, SkillDef> next) {
        Map<ResourceLocation, SkillDef> entries = Map.copyOf(new LinkedHashMap<>(next));
        Map<ResourceLocation, ResourceLocation> legacy = new LinkedHashMap<>();
        java.util.Set<ResourceLocation> ambiguous = new java.util.HashSet<>();
        for (ResourceLocation id : entries.keySet()) {
            int slash = id.getPath().lastIndexOf('/');
            if (slash < 0) continue;
            ResourceLocation flat = ResourceLocation.tryParse(
                    id.getNamespace() + ":" + id.getPath().substring(slash + 1));
            if (flat == null || entries.containsKey(flat)) continue;
            ResourceLocation first = legacy.putIfAbsent(flat, id);
            if (first != null && !first.equals(id)) ambiguous.add(flat);
        }
        legacy.keySet().removeAll(ambiguous);
        ENTRIES = entries;
        LEGACY = Map.copyOf(legacy);
    }

    @Nullable
    public static SkillDef byId(ResourceLocation id) {
        if (id == null) return null;
        SkillDef direct = ENTRIES.get(id);
        if (direct != null) return direct;
        ResourceLocation canonical = LEGACY.get(id);
        return canonical == null ? null : ENTRIES.get(canonical);
    }

    /** The current id for {@code id}: itself when registered, its path-scoped successor, or itself unchanged. */
    public static ResourceLocation canonicalId(ResourceLocation id) {
        if (id == null || ENTRIES.containsKey(id)) return id;
        return LEGACY.getOrDefault(id, id);
    }

    public static Map<ResourceLocation, SkillDef> all() {
        return ENTRIES;
    }

    /** Skills that name the given profession, in registry order. */
    public static List<SkillDef> forProfession(ResourceLocation profession) {
        List<SkillDef> out = new ArrayList<>();
        for (SkillDef skill : ENTRIES.values()) {
            if (skill.profession().equals(profession)) out.add(skill);
        }
        return out;
    }

    public static int size() {
        return ENTRIES.size();
    }
}
