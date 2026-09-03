package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reloadable semantic-performance to concrete-provider mapping. */
public final class PerformanceMappings {
    public static final String SCHEMA = "townstead:performance_mapping/v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/PerformanceMappings");
    private static volatile Map<ResourceLocation, List<Target>> mappings = Map.of();

    public record Target(String provider, ResourceLocation performance, int priority, int order) {
        public Target {
            if (provider == null || ResourceLocation.tryParse(provider) == null) {
                throw new IllegalArgumentException("provider must be a resource id");
            }
            if (performance == null) throw new IllegalArgumentException("performance is required");
        }
    }

    private PerformanceMappings() {}

    public static List<Target> targets(ResourceLocation performance) {
        return mappings.getOrDefault(performance, List.of());
    }

    static List<Target> parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        requireOnly(json, "schema", "mods", "targets");
        if (!json.has("targets") || !json.get("targets").isJsonArray()) {
            throw new IllegalArgumentException("targets must be an array");
        }
        List<Target> out = new ArrayList<>();
        int order = 0;
        for (JsonElement element : json.getAsJsonArray("targets")) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("targets must contain objects");
            JsonObject target = element.getAsJsonObject();
            requireOnly(target, "provider", "performance", "priority");
            ResourceLocation concrete = ResourceLocation.tryParse(GsonHelper.getAsString(target, "performance"));
            if (concrete == null) throw new IllegalArgumentException("target performance must be a resource id");
            out.add(new Target(GsonHelper.getAsString(target, "provider"), concrete,
                    GsonHelper.getAsInt(target, "priority", 0), order++));
        }
        if (out.isEmpty()) throw new IllegalArgumentException("targets must not be empty");
        out.sort(Comparator.comparingInt(Target::priority).reversed().thenComparingInt(Target::order));
        return List.copyOf(out);
    }

    static void replaceForTest(Map<ResourceLocation, List<Target>> next) { mappings = Map.copyOf(next); }

    private static void requireOnly(JsonObject json, String... allowed) {
        Set<String> keys = Set.of(allowed);
        for (String key : json.keySet()) if (!keys.contains(key)) {
            throw new IllegalArgumentException("unknown field '" + key + "'");
        }
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        private static final String FAMILY = "performance_mapping";

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> documents = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources(FAMILY, path -> path.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring(FAMILY.length() + 1, path.length() - 5));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement element = JsonParser.parseReader(reader);
                    if (!element.isJsonObject()) throw new IllegalArgumentException("document root must be an object");
                    documents.put(id, element.getAsJsonObject());
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read {}: {}", file, exception.getMessage());
                }
            }
            return documents;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, List<Target>> next = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                if (json.has("mods")) {
                    Boolean enabled = ModGate.evaluate(json.get("mods"));
                    if (enabled == null) { LOGGER.warn("Performance mapping {} has malformed mods gate", entry.getKey()); continue; }
                    if (!enabled) continue;
                }
                try { next.put(entry.getKey(), parse(entry.getKey(), json)); }
                catch (RuntimeException exception) {
                    LOGGER.warn("Performance mapping {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            mappings = Map.copyOf(next);
            LOGGER.info("Loaded {} semantic performance mappings", mappings.size());
        }
    }
}
