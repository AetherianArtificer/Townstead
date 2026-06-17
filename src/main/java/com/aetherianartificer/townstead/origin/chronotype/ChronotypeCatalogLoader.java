package com.aetherianartificer.townstead.origin.chronotype;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.origin.gene.types.ChronotypeGeneType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads the chronotype variant catalog from {@code data/<ns>/chronotype/*.json}. Each
 * file is one variant ({@code label} + {@code sleep_hours}); the variant id is the file
 * path, so {@code townstead_origins/chronotype/early_bird.json} declares {@code early_bird}.
 * Registered before {@link com.aetherianartificer.townstead.origin.gene.GeneJsonLoader} so
 * chronotype genes can resolve weight-only variant references at load.
 */
public final class ChronotypeCatalogLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ChronotypeCatalogLoader");
    private static final Gson GSON = new Gson();

    public ChronotypeCatalogLoader() {
        super(GSON, "chronotype");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<String, ChronotypeCatalog.Entry> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(obj, "townstead:chronotype/v1");
                int[] sleepHours = ChronotypeGeneType.tickHours(obj);
                if (sleepHours == null) {
                    LOGGER.warn("Skipping chronotype variant {} — missing/empty 'sleep_hours'", file);
                    continue;
                }
                Component label = obj.has("label")
                        ? DataPackLang.parseComponent(obj.get("label"), file.toString(), lang)
                        : Component.literal(file.getPath());
                parsed.put(file.getPath(), new ChronotypeCatalog.Entry(label, sleepHours));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse chronotype variant {}: {}", file, ex.getMessage());
            }
        }
        ChronotypeCatalog.replaceAll(parsed);
        LOGGER.info("Loaded {} chronotype variants", parsed.size());
    }
}
