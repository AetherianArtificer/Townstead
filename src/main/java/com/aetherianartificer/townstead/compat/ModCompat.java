package com.aetherianartificer.townstead.compat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ModCompat {
    private static final Map<String, Boolean> LOADED_CACHE = new ConcurrentHashMap<>();

    private ModCompat() {}

    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isBlank()) return false;
        return LOADED_CACHE.computeIfAbsent(modId, id -> ModList.get().isLoaded(id));
    }

    public static boolean isFromLoadedMod(ResourceLocation id, String modId) {
        if (id == null || modId == null || modId.isBlank()) return false;
        return isLoaded(modId) && modId.equals(id.getNamespace());
    }

    public static boolean matchesLoadedModPath(ResourceLocation id, String modId, String path) {
        if (path == null || path.isBlank()) return false;
        return isFromLoadedMod(id, modId) && path.equals(id.getPath());
    }
}
