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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the {@code townsteadNodeItem} for a building type, keyed either by
 * the building-type id or by the {@code (iconU, iconV)} sprite-sheet slot the
 * type advertises.
 *
 * <p>Two lookup paths are used: first the datapack override map populated by
 * {@link CatalogDataLoader}, then a classpath scan of the compat or vanilla
 * building-type JSON. Results are cached for the lifetime of the session.
 *
 * <p>Used by mixins in both the rich Townstead catalog path
 * ({@code BlueprintScreenMixin}) and the vanilla MCA catalog path
 * ({@code LegacyImageButtonMixin}).
 */
public final class BuildingIconResolver {
    private static final ConcurrentHashMap<Long, Optional<ResourceLocation>> UV_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Optional<ResourceLocation>> TYPE_CACHE = new ConcurrentHashMap<>();

    private BuildingIconResolver() {}

    public static Optional<ResourceLocation> nodeItemForIconUv(int u, int v) {
        long key = (((long) u) << 32) ^ (v & 0xFFFFFFFFL);
        return UV_CACHE.computeIfAbsent(key, ignored -> {
            ResourceLocation resolved = null;
            for (BuildingType bt : BuildingTypes.getInstance()) {
                if (bt.iconU() != u || bt.iconV() != v) continue;
                Optional<ResourceLocation> candidate = nodeItemForType(bt.name());
                if (candidate.isEmpty() || !BuiltInRegistries.ITEM.containsKey(candidate.get())) continue;
                if (resolved == null) {
                    resolved = candidate.get();
                } else if (!resolved.equals(candidate.get())) {
                    // Ambiguous slot: bail rather than render one mod's icon for another.
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(resolved);
        });
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
        try {
            String relPath = buildingTypeName.startsWith("compat/")
                    ? "townstead_compat/building_types/" + buildingTypeName + ".json"
                    : "data/mca/building_types/" + buildingTypeName + ".json";
            ClassLoader cl = BuildingIconResolver.class.getClassLoader();
            if (cl == null) return Optional.empty();
            Enumeration<URL> urls = cl.getResources(relPath);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                        InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                    if (obj.has("townsteadNodeItem")) {
                        //? if >=1.21 {
                        return Optional.of(ResourceLocation.parse(obj.get("townsteadNodeItem").getAsString()));
                        //?} else {
                        /*return Optional.of(new ResourceLocation(obj.get("townsteadNodeItem").getAsString()));
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
