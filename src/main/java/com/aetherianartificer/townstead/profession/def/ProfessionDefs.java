package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side registry of data-driven {@link ProfessionDef}s, replaced each datapack reload by
 * {@link ProfessionDataLoader}.
 */
public final class ProfessionDefs {

    private static volatile Map<ResourceLocation, ProfessionDef> ENTRIES = Map.of();

    private ProfessionDefs() {}

    public static void replaceAll(Map<ResourceLocation, ProfessionDef> next) {
        ENTRIES = Map.copyOf(new LinkedHashMap<>(next));
    }

    @Nullable
    public static ProfessionDef byId(ResourceLocation id) {
        return id == null ? null : ENTRIES.get(id);
    }

    public static Map<ResourceLocation, ProfessionDef> all() {
        return ENTRIES;
    }

    public static int size() {
        return ENTRIES.size();
    }
}
