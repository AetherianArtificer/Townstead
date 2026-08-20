package com.aetherianartificer.townstead.work.job;

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
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reload-safe registry for {@code data/<namespace>/work_job/*.json}. */
public final class WorkJobs {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/WorkJobs");
    private static volatile List<WorkJobDef> DEFINITIONS = List.of();

    private WorkJobs() {}

    public static List<WorkJobDef> all() {
        return DEFINITIONS;
    }

    public static @Nullable WorkJobDef first(ResourceLocation task, ResourceLocation executor) {
        for (WorkJobDef def : DEFINITIONS) {
            if (def.task().equals(task) && def.executor().equals(executor)) return def;
        }
        return null;
    }

    public static List<WorkJobDef> forExecutor(ResourceLocation executor) {
        List<WorkJobDef> out = new ArrayList<>();
        for (WorkJobDef def : DEFINITIONS) if (def.executor().equals(executor)) out.add(def);
        return List.copyOf(out);
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resources, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : resources
                    .listResources("work_job", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("work_job/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read work job {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resources,
                             ProfilerFiller profiler) {
            List<WorkJobDef> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validate(json, WorkJobDef.SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Work job {} rejected: {}", entry.getKey(), ex.getMessage());
                    continue;
                }
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) continue;
                WorkJobDef def = WorkJobDef.parse(entry.getKey(), json);
                if (def == null) {
                    LOGGER.warn("Invalid work job {}", entry.getKey());
                    continue;
                }
                loaded.add(def);
            }
            DEFINITIONS = List.copyOf(loaded);
            LOGGER.info("Loaded {} work job definitions", loaded.size());
        }
    }
}
