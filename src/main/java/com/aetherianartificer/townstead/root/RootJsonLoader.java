package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads {@link Root} from {@code data/<ns>/root/*.json}, with legacy {@code origin/} fallback. */
public final class RootJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/RootJsonLoader");
    private static final Gson GSON = new Gson();

    public RootJsonLoader() {
        super(GSON, "root");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Root> parsed = new LinkedHashMap<>();
        loadLegacyOrigins(resourceManager, lang, parsed);
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                parseRoot(file, GsonHelper.convertToJsonObject(entry.getValue(), file.toString()),
                        lang, parsed, true);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse root {}: {}", file, ex.getMessage());
            }
        }
        RootRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} roots", parsed.size());
    }

    private static void loadLegacyOrigins(ResourceManager resourceManager, Map<String, String> lang,
            Map<ResourceLocation, Root> parsed) {
        Map<ResourceLocation, Resource> resources = resourceManager.listResources("origin",
                id -> id.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> entry : resources.entrySet()) {
            ResourceLocation location = entry.getKey();
            String path = location.getPath();
            if (!path.startsWith("origin/") || !path.endsWith(".json")) continue;
            //? if >=1.21 {
            ResourceLocation file = ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                    path.substring("origin/".length(), path.length() - ".json".length()));
            //?} else {
            /*ResourceLocation file = new ResourceLocation(location.getNamespace(),
                    path.substring("origin/".length(), path.length() - ".json".length()));
            *///?}
            try (InputStream in = entry.getValue().open();
                    InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                if (obj != null) parseRoot(file, obj, lang, parsed, false);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse legacy origin {}: {}", location, ex.getMessage());
            }
        }
    }

    private static void parseRoot(ResourceLocation file, JsonObject obj, Map<String, String> lang,
            Map<ResourceLocation, Root> parsed, boolean currentSchema) {
        String ctx = file.toString();
        try {
            TownsteadSchema.validate(obj, currentSchema ? "townstead:root/v1" : "townstead:origin/v1");
            Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
            ResourceLocation species = RootJsonParsing.optionalId(obj, "species", ctx, LOGGER);
            ResourceLocation ancestry = RootJsonParsing.optionalId(obj, "ancestry", ctx, LOGGER);
            ResourceLocation lineage = RootJsonParsing.optionalId(obj, "lineage", ctx, LOGGER);
            Demonym demonym = RootJsonParsing.demonym(obj, ctx, lang);
            Component backstory = RootJsonParsing.backstory(obj, ctx, lang);
            Genome genome = RootJsonParsing.genes(obj, ctx, LOGGER);
            SpawnBias spawnBias = RootJsonParsing.spawnBias(obj, ctx, LOGGER);
            ResourceLocation kinFlesh = RootJsonParsing.optionalId(obj, "kin_flesh", ctx, LOGGER);
            boolean eatsSapients = net.minecraft.util.GsonHelper.getAsBoolean(obj, "eats_sapients", false);
            parsed.put(file, new Root(file, displayName, species, ancestry, lineage, demonym, backstory, genome, spawnBias,
                    kinFlesh, eatsSapients));
        } catch (Exception ex) {
            LOGGER.warn("Failed to parse root {}: {}", file, ex.getMessage());
        }
    }
}
