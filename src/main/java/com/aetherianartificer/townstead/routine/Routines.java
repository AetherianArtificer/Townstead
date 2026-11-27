package com.aetherianartificer.townstead.routine;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.data.ModGate;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The loaded {@code routine} files from every namespace, highest priority first. */
public final class Routines {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Routines");
    private static volatile List<RoutineDef> ROUTINES = List.of();

    private Routines() {}

    public static List<RoutineDef> all() {
        return ROUTINES;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resources, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : resources
                    .listResources("routine", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("routine/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read routine {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resources, ProfilerFiller profiler) {
            List<RoutineDef> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validateRequired(json, RoutineDef.SCHEMA);
                    if (json.has("mods")) {
                        Boolean enabled = ModGate.evaluate(json.get("mods"));
                        if (enabled == null) throw new IllegalArgumentException("'mods' is malformed");
                        if (!enabled) continue;
                    }
                    loaded.add(RoutineDef.parse(entry.getKey(), json));
                } catch (RuntimeException ex) {
                    LOGGER.warn("Routine {} rejected: {}", entry.getKey(), ex.getMessage());
                }
            }
            loaded.sort(Comparator.comparingInt(RoutineDef::priority).reversed().thenComparing(r -> r.id().toString()));
            ROUTINES = List.copyOf(loaded);
            LOGGER.info("Loaded {} routines", loaded.size());
        }
    }
}
