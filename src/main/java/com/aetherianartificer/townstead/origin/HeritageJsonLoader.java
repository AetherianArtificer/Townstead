package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
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
 * Loads {@link HeritageProfile}s from {@code data/<ns>/heritage/*.json}: named
 * ancestry blends (Half-Elf, …) that label a villager's {@link Heritage} vector.
 */
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
        Map<ResourceLocation, HeritageProfile> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String ctx = file.toString();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), ctx);
                TownsteadSchema.validate(obj, "townstead:heritage/v1");
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
                Demonym demonym = OriginJsonParsing.demonym(obj, ctx, lang);
                Component backstory = OriginJsonParsing.backstory(obj, ctx, lang);
                int priority = GsonHelper.getAsInt(obj, "priority", 0);
                Map<ResourceLocation, GeneRange> match = parseMatch(obj, ctx);
                if (match.isEmpty()) {
                    LOGGER.warn("Skipping heritage {} — empty or invalid 'match'", file);
                    continue;
                }
                parsed.put(file, new HeritageProfile(file, displayName, demonym, backstory, priority, match));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse heritage {}: {}", file, ex.getMessage());
            }
        }
        HeritageRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} heritage profiles", parsed.size());
    }

    /** {@code "match": { "<ancestry>": {min,max} | <fraction> }}; a bare number is an exact share. */
    private static Map<ResourceLocation, GeneRange> parseMatch(JsonObject obj, String ctx) {
        Map<ResourceLocation, GeneRange> out = new LinkedHashMap<>();
        if (!obj.has("match") || !obj.get("match").isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("match").entrySet()) {
            ResourceLocation ancestry = DataPackLang.parseId(e.getKey());
            if (ancestry == null) {
                LOGGER.warn("{} — match ancestry '{}' is not a valid id, skipping", ctx, e.getKey());
                continue;
            }
            if (e.getValue().isJsonObject()) {
                JsonObject band = e.getValue().getAsJsonObject();
                out.put(ancestry, new GeneRange(GsonHelper.getAsFloat(band, "min", 0f),
                        GsonHelper.getAsFloat(band, "max", 1f)));
            } else if (e.getValue().isJsonPrimitive()) {
                float exact = e.getValue().getAsFloat();
                out.put(ancestry, new GeneRange(exact, exact));
            }
        }
        return out;
    }
}
