package com.aetherianartificer.townstead.compat;

import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.neoforged.fml.ModList;
//?} else if forge {
/*import net.minecraftforge.fml.ModList;
*///?}

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class ModCompat {
    private static final Map<String, Boolean> LOADED_CACHE = new ConcurrentHashMap<>();

    /**
     * Mods that furnish a kitchen: cooking stations to work at and the blocks the kitchen tiers
     * are built from. Any ONE of them makes a cook possible, which is why nothing may gate the
     * trade on Farmer's Delight by name — the tier lines are role tags every provider
     * contributes to, so a kitchen can be built, staffed and worked without it.
     */
    public static final List<String> KITCHEN_PROVIDERS = List.of(
            "farmersdelight", "farm_and_charm", "kaleidoscope_cookery");

    /**
     * Compat paths whose requirements are satisfiable by more than one provider mod (their
     * tier lines are role tags each provider contributes to). Matched by prefix; available
     * when ANY listed mod is loaded. Other compat paths gate on the mod id in the path.
     */
    private static final Map<String, List<String>> ANY_PROVIDER_PREFIXES = Map.of(
            "compat/farmersdelight/kitchen", KITCHEN_PROVIDERS);

    private ModCompat() {}

    public static boolean isLoaded(String modId) {
        if (modId == null || modId.isBlank()) return false;
        return LOADED_CACHE.computeIfAbsent(modId, id -> ModList.get().isLoaded(id));
    }

    /** Whether any one of these mods is present. */
    public static boolean anyLoaded(List<String> modIds) {
        if (modIds == null) return false;
        for (String modId : modIds) {
            if (isLoaded(modId)) return true;
        }
        return false;
    }

    /** Whether anything installed can furnish a kitchen; see {@link #KITCHEN_PROVIDERS}. */
    public static boolean hasKitchenProvider() {
        return anyLoaded(KITCHEN_PROVIDERS);
    }

    public static boolean isFromLoadedMod(ResourceLocation id, String modId) {
        if (id == null || modId == null || modId.isBlank()) return false;
        return isLoaded(modId) && modId.equals(id.getNamespace());
    }

    public static boolean matchesLoadedModPath(ResourceLocation id, String modId, String path) {
        if (path == null || path.isBlank()) return false;
        return isFromLoadedMod(id, modId) && path.equals(id.getPath());
    }

    /**
     * Extract the mod ID from a compat-prefixed path like "compat/farmersdelight/kitchen_l1".
     * Returns null for non-compat paths.
     */
    public static String extractCompatModId(String path) {
        if (path == null || !path.startsWith("compat/")) return null;
        String[] parts = path.split("/");
        return parts.length >= 2 ? parts[1] : null;
    }

    /**
     * Returns true if the given path is either not a compat path, or the required mod is loaded.
     * Use this to gate any compat-prefixed features (building types, patterns, etc.).
     */
    public static boolean isCompatAvailable(String path) {
        if (path != null) {
            for (Map.Entry<String, List<String>> entry : ANY_PROVIDER_PREFIXES.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    return entry.getValue().stream().anyMatch(ModCompat::isLoaded);
                }
            }
        }
        String modId = extractCompatModId(path);
        return modId == null || isLoaded(modId);
    }

    /**
     * Loaded mods that actually provide a compat building. Shared families return every loaded
     * provider instead of leaking the legacy mod id embedded in their stable building-type path.
     */
    public static List<String> loadedCompatProviders(String path) {
        if (path != null) {
            for (Map.Entry<String, List<String>> entry : ANY_PROVIDER_PREFIXES.entrySet()) {
                if (path.startsWith(entry.getKey())) {
                    return entry.getValue().stream().filter(ModCompat::isLoaded).collect(Collectors.toList());
                }
            }
        }
        String modId = extractCompatModId(path);
        return modId != null && isLoaded(modId) ? List.of(modId) : List.of();
    }
}
