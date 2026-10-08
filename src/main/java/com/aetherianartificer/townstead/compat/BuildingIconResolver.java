package com.aetherianartificer.townstead.compat;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the Townstead {@code catalog.node_item} for a building type.
 *
 * <p>Two lookup paths are used: first the datapack override map populated by
 * {@link CatalogDataLoader}, then a classpath scan of the compat or vanilla
 * building-type JSON. Results are cached for the lifetime of the session.
 * Townstead item identity is keyed by building type. The UV lookup exists only as a bridge for
 * legacy MCA render methods which expose the atlas coordinates but not the originating type.
 */
public final class BuildingIconResolver {
    private static final ConcurrentHashMap<Long, Optional<ResourceLocation>> UV_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<ResourceLocation>> TYPE_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Set<String>> TYPES_BY_UV = new ConcurrentHashMap<>();

    private BuildingIconResolver() {}

    public static Optional<ResourceLocation> nodeItemForIconUv(int u, int v) {
        long key = uvKey(u, v);
        return UV_CACHE.computeIfAbsent(key, ignored -> {
            ResourceLocation resolved = null;
            Set<String> typeNames = new LinkedHashSet<>(TYPES_BY_UV.getOrDefault(key, Set.of()));
            for (BuildingType type : BuildingTypes.getInstance()) {
                if (type.iconU() == u && type.iconV() == v) typeNames.add(type.name());
            }
            for (String typeName : typeNames) {
                Optional<ResourceLocation> candidate = nodeItemForType(typeName);
                if (candidate.isEmpty() || !BuiltInRegistries.ITEM.containsKey(candidate.get())) continue;
                if (resolved == null) {
                    resolved = candidate.get();
                } else if (!resolved.equals(candidate.get())) {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(resolved);
        });
    }

    /** Records MCA's legacy render address for a type; it does not define Townstead's item. */
    public static void registerBuildingTypeIcon(String buildingTypeName, int u, int v) {
        if (buildingTypeName == null || buildingTypeName.isBlank()) return;
        long key = uvKey(u, v);
        TYPES_BY_UV.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet())
                .add(buildingTypeName);
        UV_CACHE.remove(key);
    }

    public static void beginBuildingTypeReload() {
        TYPES_BY_UV.clear();
        invalidate();
    }

    private static long uvKey(int u, int v) {
        return (((long) u) << 32) ^ (v & 0xFFFFFFFFL);
    }

    public static Optional<ResourceLocation> nodeItemForType(String buildingTypeName) {
        if (buildingTypeName == null) return Optional.empty();
        return TYPE_CACHE.computeIfAbsent(buildingTypeName, name -> {
            Optional<ResourceLocation> datapack = CatalogDataLoader.overrideFor(name).nodeItem();
            if (datapack.isPresent()) return datapack;
            return scanBuildingTypeJson(name);
        });
    }

    private static Optional<ResourceLocation> scanBuildingTypeJson(String buildingTypeName) {
        Optional<ResourceLocation> extended = scanNodeItemJson(
                "data/townstead/extended_buildings/" + buildingTypeName + ".json", true);
        if (extended.isPresent()) return extended;
        Optional<ResourceLocation> loaded = scanNodeItemJson(
                "data/mca/building_types/" + buildingTypeName + ".json", false);
        if (loaded.isPresent()) return loaded;
        // Compat building definitions are packaged here until ConditionalCompatPack
        // exposes them as data/mca resources for an installed integration mod.
        return scanNodeItemJson("townstead_compat/building_types/" + buildingTypeName + ".json", false);
    }

    private static Optional<ResourceLocation> scanNodeItemJson(String relPath, boolean extendedFormat) {
        try {
            ClassLoader cl = BuildingIconResolver.class.getClassLoader();
            if (cl == null) return Optional.empty();
            Enumeration<URL> urls = cl.getResources(relPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    String raw = null;
                    if (extendedFormat && obj.has("catalog") && obj.get("catalog").isJsonObject()) {
                        JsonObject catalog = obj.getAsJsonObject("catalog");
                        if (catalog.has("node_item")) raw = catalog.get("node_item").getAsString();
                    } else if (!extendedFormat && obj.has("townsteadNodeItem")) {
                        raw = obj.get("townsteadNodeItem").getAsString();
                    }
                    if (raw != null) {
                        //? if >=1.21 {
                        return Optional.of(ResourceLocation.parse(raw));
                        //?} else {
                        /*return Optional.of(new ResourceLocation(raw));
                        *///?}
                    }
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static void invalidate() {
        UV_CACHE.clear();
        TYPE_CACHE.clear();
    }
}
