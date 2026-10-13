package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How many times each building may appear in one generated village, from
 * {@code data/<ns>/village_layout/<path>.json}. Template pools pick buildings by weight alone; a
 * building that reaches its maximum is skipped for the rest of that village.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:village_layout/v1",
 *   "structure": "townstead_cavalrosia:village/town",
 *   "max": { "townstead_cavalrosia:village/town/rose_chapel": 1 }
 * }
 * }</pre>
 */
public final class VillageLayouts {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/VillageLayouts");
    private static final String SCHEMA = "townstead:village_layout/v1";
    private static volatile Map<ResourceLocation, Integer> maxByTemplate = Map.of();

    private VillageLayouts() {}

    /** The most copies of a structure template one village may hold, or null when unlimited. */
    public static @Nullable Integer max(@Nullable ResourceLocation template) {
        return template == null ? null : maxByTemplate.get(template);
    }

    public static boolean hasLimits() {
        return !maxByTemplate.isEmpty();
    }

    static void replace(Map<ResourceLocation, Integer> limits) {
        maxByTemplate = Map.copyOf(limits);
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new Gson();

        public Loader() {
            super(GSON, "village_layout");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, Integer> limits = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    TownsteadSchema.validate(root, SCHEMA);
                    JsonObject max = GsonHelper.getAsJsonObject(root, "max", new JsonObject());
                    for (Map.Entry<String, JsonElement> limit : max.entrySet()) {
                        ResourceLocation template = DataPackLang.parseId(limit.getKey());
                        if (template == null) throw new IllegalArgumentException("'" + limit.getKey() + "' is not a template id");
                        int count = GsonHelper.convertToInt(limit.getValue(), limit.getKey());
                        if (count < 0) throw new IllegalArgumentException("Limit for " + template + " must not be negative");
                        limits.put(template, count);
                    }
                } catch (RuntimeException error) {
                    LOGGER.warn("Village layout {} rejected: {}", entry.getKey(), error.getMessage());
                }
            }
            replace(limits);
            LOGGER.info("Loaded {} village building limit(s)", limits.size());
        }
    }
}
