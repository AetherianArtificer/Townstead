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
import java.util.Set;

/** Reload-safe registry for {@code data/<namespace>/work_job/*.json}. */
public final class WorkJobs {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/WorkJobs");
    private static volatile List<WorkJobDef> DEFINITIONS = List.of();
    private static volatile Set<ResourceLocation> DECLARED_TASKS = Set.of();

    private WorkJobs() {}

    public static List<WorkJobDef> all() {
        return DEFINITIONS;
    }

    public static @Nullable WorkJobDef first(ResourceLocation task, ResourceLocation type) {
        for (WorkJobDef def : DEFINITIONS) {
            if (def.task().equals(task) && def.type().equals(type)) return def;
        }
        return null;
    }

    public static @Nullable WorkJobDef byId(ResourceLocation id) {
        for (WorkJobDef def : DEFINITIONS) if (def.id().equals(id)) return def;
        return null;
    }

    public static List<WorkJobDef> forType(ResourceLocation type) {
        List<WorkJobDef> out = new ArrayList<>();
        for (WorkJobDef def : DEFINITIONS) if (def.type().equals(type)) out.add(def);
        return List.copyOf(out);
    }

    /** Every Job document attached to a profession-facing task id. */
    public static List<WorkJobDef> forTask(ResourceLocation task) {
        if (task == null) return List.of();
        List<WorkJobDef> out = new ArrayList<>();
        for (WorkJobDef def : DEFINITIONS) if (def.task().equals(task)) out.add(def);
        return List.copyOf(out);
    }

    /** Whether loaded Job data supplies an executor for this task id. */
    public static boolean handlesTask(@Nullable ResourceLocation task) {
        if (task == null) return false;
        for (WorkJobDef def : DEFINITIONS) if (def.task().equals(task)) return true;
        return false;
    }

    /** Whether a valid Job document declares this task, including a currently unmet mod gate. */
    public static boolean knowsTask(@Nullable ResourceLocation task) {
        return task != null && DECLARED_TASKS.contains(task);
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
            Set<ResourceLocation> declaredTasks = new java.util.LinkedHashSet<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validateRequired(json, WorkJobDef.SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Work job {} rejected: {}", entry.getKey(), ex.getMessage());
                    continue;
                }
                WorkJobDef def = WorkJobDef.parse(entry.getKey(), json);
                if (def == null) {
                    LOGGER.warn("Invalid work job {}", entry.getKey());
                    continue;
                }
                declaredTasks.add(def.task());
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) continue;
                loaded.add(def);
            }
            DEFINITIONS = List.copyOf(loaded);
            DECLARED_TASKS = Set.copyOf(declaredTasks);
            LOGGER.info("Loaded {} work job definitions", loaded.size());
        }
    }
}
