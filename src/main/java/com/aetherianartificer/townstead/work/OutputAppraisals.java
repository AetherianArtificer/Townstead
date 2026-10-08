package com.aetherianartificer.townstead.work;

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
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reloadable {@code output_appraisal/v1} registry. */
public final class OutputAppraisals {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/OutputAppraisals");
    private static volatile List<OutputAppraisalDef> DEFINITIONS = List.of();

    private OutputAppraisals() {}

    public static @Nullable OutputAppraisal.Appraisal appraise(ItemStack stack) {
        for (OutputAppraisalDef definition : DEFINITIONS) {
            OutputAppraisal.Appraisal result = definition.appraise(stack);
            if (result != null) return result;
        }
        return null;
    }

    static void replaceForTest(List<OutputAppraisalDef> definitions) {
        DEFINITIONS = List.copyOf(definitions);
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources("output_appraisal", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("output_appraisal/".length(), path.length() - 5));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read output appraisal {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<OutputAppraisalDef> loaded = new ArrayList<>();
            prepared.entrySet().stream().sorted(Map.Entry.comparingByKey(
                    Comparator.comparing(ResourceLocation::toString))).forEach(entry -> {
                JsonObject json = entry.getValue();
                try {
                    TownsteadSchema.validateRequired(json, OutputAppraisalDef.SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Output appraisal {} rejected: {}", entry.getKey(), ex.getMessage());
                    return;
                }
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) return;
                OutputAppraisalDef definition = OutputAppraisalDef.parse(entry.getKey(), json);
                if (definition == null) {
                    LOGGER.warn("Output appraisal {} rejected: malformed selector, path, or tiers", entry.getKey());
                    return;
                }
                loaded.add(definition);
            });
            DEFINITIONS = List.copyOf(loaded);
            if (!loaded.isEmpty()) LOGGER.info("Loaded {} output appraisal definitions", loaded.size());
        }
    }
}
