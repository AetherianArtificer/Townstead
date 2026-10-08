package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Additive block-owned recipe associations loaded from
 * {@code data/<block namespace>/tags/recipe_type/<block path>.json}.
 *
 * <p>Recipe types are not a vanilla tagged registry on every supported loader. Loading the same
 * resource shape ourselves gives datapacks the expected {@code replace}/{@code values} merge
 * contract without pretending the association belongs in a Townstead workstation document.</p>
 */
public final class WorkstationRecipeTypes {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/WorkstationRecipeTypes");
    private static volatile Map<ResourceLocation, Set<ResourceLocation>> BY_BLOCK = Map.of();

    private WorkstationRecipeTypes() {}

    public static Set<ResourceLocation> forBlock(ResourceLocation block) {
        return BY_BLOCK.getOrDefault(block, Set.of());
    }

    static void replaceAll(Map<ResourceLocation, Set<ResourceLocation>> values) {
        Map<ResourceLocation, Set<ResourceLocation>> frozen = new LinkedHashMap<>();
        values.forEach((key, value) -> frozen.put(key, Set.copyOf(value)));
        BY_BLOCK = Map.copyOf(frozen);
        Workstations.refreshV2CompatibilityViews();
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, Set<ResourceLocation>>> {
        @Override
        protected Map<ResourceLocation, Set<ResourceLocation>> prepare(
                ResourceManager resources, ProfilerFiller profiler) {
            Map<ResourceLocation, Set<ResourceLocation>> out = new LinkedHashMap<>();
            for (ResourceLocation file : resources
                    .listResources("tags/recipe_type", id -> id.getPath().endsWith(".json")).keySet()) {
                String path = file.getPath();
                String blockPath = path.substring("tags/recipe_type/".length(), path.length() - 5);
                ResourceLocation block = ResourceLocation.tryParse(file.getNamespace() + ":" + blockPath);
                if (block == null) continue;
                LinkedHashSet<ResourceLocation> merged = new LinkedHashSet<>();
                try {
                    List<Resource> stack = resources.getResourceStack(file);
                    for (Resource resource : stack) {
                        try (Reader reader = resource.openAsReader()) {
                            JsonElement parsed = JsonParser.parseReader(reader);
                            if (!parsed.isJsonObject()) throw new IllegalArgumentException("root must be an object");
                            JsonObject json = parsed.getAsJsonObject();
                            if (json.has("replace") && json.get("replace").getAsBoolean()) merged.clear();
                            if (!json.has("values") || !json.get("values").isJsonArray()) {
                                throw new IllegalArgumentException("values must be an array");
                            }
                            for (JsonElement value : json.getAsJsonArray("values")) {
                                String raw;
                                if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                                    raw = value.getAsString();
                                } else if (value.isJsonObject() && value.getAsJsonObject().has("id")) {
                                    raw = value.getAsJsonObject().get("id").getAsString();
                                } else throw new IllegalArgumentException("recipe type values must be ids");
                                ResourceLocation type = ResourceLocation.tryParse(raw);
                                if (type == null) throw new IllegalArgumentException("invalid recipe type " + value);
                                merged.add(type);
                            }
                        }
                    }
                    if (!merged.isEmpty()) out.put(block, Set.copyOf(merged));
                } catch (Exception exception) {
                    LOGGER.warn("Recipe types for block {} rejected: {}", block, exception.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, Set<ResourceLocation>> prepared,
                             ResourceManager resources, ProfilerFiller profiler) {
            replaceAll(prepared);
            LOGGER.info("Loaded recipe type associations for {} workstation blocks", prepared.size());
        }
    }
}
