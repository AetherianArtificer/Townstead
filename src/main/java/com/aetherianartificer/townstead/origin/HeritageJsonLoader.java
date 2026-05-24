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
import java.util.List;
import java.util.Map;

/** Loads {@link Heritage} from {@code data/<ns>/heritage/*.json}. */
public final class HeritageJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/HeritageJsonLoader");
    private static final Gson GSON = new Gson();

    public HeritageJsonLoader() {
        super(GSON, "heritage");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Heritage> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String ctx = file.toString();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), ctx);
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
                List<ResourceLocation> ancestries = OriginJsonParsing.idList(obj, "ancestries");
                Demonym demonym = OriginJsonParsing.demonym(obj, ctx, lang);
                Component backstory = OriginJsonParsing.backstory(obj, ctx, lang);
                Genome overrides = OriginJsonParsing.genome(obj, "genome_overrides", ctx, LOGGER);
                parsed.put(file, new Heritage(file, displayName, ancestries, demonym, backstory, overrides));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse heritage {}: {}", file, ex.getMessage());
            }
        }
        HeritageRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} origin heritages", parsed.size());
    }
}
