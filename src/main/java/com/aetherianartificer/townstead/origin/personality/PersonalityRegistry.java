package com.aetherianartificer.townstead.origin.personality;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side registry of data-pack-loaded {@link PersonalityDef}s, populated by
 * {@link PersonalityJsonLoader} on each resource reload (atomically rebuilt map, like
 * {@code SpeciesRegistry}).
 */
public final class PersonalityRegistry {

    private static volatile Map<ResourceLocation, PersonalityDef> ENTRIES = Map.of();

    private PersonalityRegistry() {}

    static void replaceAll(Map<ResourceLocation, PersonalityDef> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static PersonalityDef byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static List<PersonalityDef> all() {
        return List.copyOf(ENTRIES.values());
    }

    public static int size() {
        return ENTRIES.size();
    }
}
