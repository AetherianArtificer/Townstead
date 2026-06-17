package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-driven {@link SkillDef}s, replaced each datapack reload by
 * {@link ProfessionDataLoader}.
 */
public final class SkillDefs {

    private static volatile Map<ResourceLocation, SkillDef> ENTRIES = Map.of();

    private SkillDefs() {}

    public static void replaceAll(Map<ResourceLocation, SkillDef> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static SkillDef byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
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
