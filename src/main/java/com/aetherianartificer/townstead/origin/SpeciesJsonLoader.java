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

/** Loads {@link Species} from {@code data/<ns>/species/*.json}. */
public final class SpeciesJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/SpeciesJsonLoader");
    private static final Gson GSON = new Gson();

    public SpeciesJsonLoader() {
        super(GSON, "species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Species> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), file.toString(), lang);
                String shape = GsonHelper.getAsString(obj, "shape", "humanoid");
                parsed.put(file, new Species(file, displayName, shape));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse species {}: {}", file, ex.getMessage());
            }
        }
        SpeciesRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} origin species", parsed.size());
    }
}
