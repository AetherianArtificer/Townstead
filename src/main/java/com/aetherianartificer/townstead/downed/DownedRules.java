package com.aetherianartificer.townstead.downed;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which villagers are downed instead of dying, besides Personas. Each file in
 * {@code data/<ns>/downed/} names who it covers with a Pheno condition:
 * <pre>{ "when": { "type": "pheno:profession", "profession": "mca:guard" } }</pre>
 */
public final class DownedRules {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Downed");
    private static final String FOLDER = "downed";
    private static volatile List<Condition> rules = List.of();

    private DownedRules() {}

    public static boolean covers(LivingEntity entity) {
        for (Condition rule : rules) {
            try {
                if (rule.test(new ConditionContext(entity))) return true;
            } catch (RuntimeException ignored) {
                // A rule that cannot read this entity does not cover it.
            }
        }
        return false;
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(FOLDER,
                    path -> path.getPath().endsWith(".json")).entrySet()) {
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(entry.getKey(), parsed.getAsJsonObject());
                    else LOGGER.warn("Downed rule {}: the root must be an object", entry.getKey());
                } catch (Exception e) {
                    LOGGER.warn("Downed rule {}: {}", entry.getKey(), e.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Condition> next = new java.util.ArrayList<>();
            files.forEach((file, json) -> {
                Condition when = Conditions.parse(json.get("when"));
                if (when == null) LOGGER.warn("Downed rule {}: \"when\" must be a known Pheno condition", file);
                else next.add(when);
            });
            rules = List.copyOf(next);
        }
    }
}
