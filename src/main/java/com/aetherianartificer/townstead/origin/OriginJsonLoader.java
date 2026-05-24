package com.aetherianartificer.townstead.origin;

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

/** Loads {@link Origin} from {@code data/<ns>/origin/*.json}. */
public final class OriginJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/OriginJsonLoader");
    private static final Gson GSON = new Gson();

    public OriginJsonLoader() {
        super(GSON, "origin");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Origin> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String ctx = file.toString();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), ctx);
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
                ResourceLocation species = OriginJsonParsing.optionalId(obj, "species", ctx, LOGGER);
                ResourceLocation ancestry = OriginJsonParsing.optionalId(obj, "ancestry", ctx, LOGGER);
                ResourceLocation heritage = OriginJsonParsing.optionalId(obj, "heritage", ctx, LOGGER);
                Demonym demonym = OriginJsonParsing.demonym(obj, ctx, lang);
                Component backstory = OriginJsonParsing.backstory(obj, ctx, lang);
                Genome overrides = OriginJsonParsing.genome(obj, "genome_overrides", ctx, LOGGER);
                parsed.put(file, new Origin(file, displayName, species, ancestry, heritage, demonym, backstory, overrides));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse origin {}: {}", file, ex.getMessage());
            }
        }
        OriginRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} origins", parsed.size());
    }
}
