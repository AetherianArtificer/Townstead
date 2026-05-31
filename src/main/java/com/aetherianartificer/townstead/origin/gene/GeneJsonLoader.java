package com.aetherianartificer.townstead.origin.gene;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
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
import java.util.Optional;

/**
 * Loads {@link Gene}s from {@code data/<ns>/gene/*.json}. Each file names a
 * {@link GeneType} via {@code "type"}; the type parses its own config. Common
 * fields ({@code display_name}, {@code description}, {@code category}) are parsed
 * here. Unknown/invalid types are skipped with a warning.
 */
public final class GeneJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/GeneJsonLoader");
    private static final Gson GSON = new Gson();

    public GeneJsonLoader() {
        super(GSON, "gene");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Gene> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                String typeKey = GsonHelper.getAsString(obj, "type", "");
                Optional<GeneType> type = GeneTypes.get(typeKey);
                if (type.isEmpty()) {
                    LOGGER.warn("Skipping gene {} — unknown type '{}'", file, typeKey);
                    continue;
                }
                GeneInstance instance = type.get().parse(obj, lang);
                if (instance == null) {
                    LOGGER.warn("Skipping gene {} — invalid config for type '{}'", file, typeKey);
                    continue;
                }
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), file.toString(), lang);
                Component description = obj.has("description")
                        ? DataPackLang.parseComponent(obj.get("description"), file + ".description", lang)
                        : null;
                String category = GsonHelper.getAsString(obj, "category", "general");
                Dominance dominance = Dominance.fromString(GsonHelper.getAsString(obj, "dominance", "dominant"));
                ResourceLocation locus = obj.has("locus")
                        ? DataPackLang.parseId(GsonHelper.getAsString(obj, "locus", ""))
                        : null;
                int weight = Math.max(1, GsonHelper.getAsInt(obj, "weight", 1));
                parsed.put(file, new Gene(file, displayName, description, category,
                        dominance, locus, weight, instance));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse gene {}: {}", file, ex.getMessage());
            }
        }
        GeneRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} genes", parsed.size());
    }
}
