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
        outfitStaging = new LinkedHashMap<>();
        stateStaging = new LinkedHashMap<>();
        // Legacy origin/ files load first; a root/ file with the same id replaces its origin.
        Map<ResourceLocation, JsonElement> legacy = com.aetherianartificer.townstead.data.DataDocuments.resolve(
                "root", readLegacyOrigins(resourceManager), resourceManager);
        Map<ResourceLocation, JsonElement> current = com.aetherianartificer.townstead.data.DataDocuments.resolve(
                "root", entries, resourceManager);
        Map<ResourceLocation, JsonElement> all = new LinkedHashMap<>(legacy);
        all.putAll(current);
        com.aetherianartificer.townstead.data.NameCollisions.qualify("root", all, resourceManager, lang);
        for (Map.Entry<ResourceLocation, JsonElement> entry : all.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                parseRoot(file, GsonHelper.convertToJsonObject(entry.getValue(), file.toString()),
                        lang, parsed, current.containsKey(file));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse root {}: {}", file, ex.getMessage());
            }
        }
        RootRegistry.replaceAll(parsed);
        java.util.Set<String> namespaces = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : parsed.keySet()) namespaces.add(id.getNamespace());
        com.aetherianartificer.townstead.data.PackInfo.record(resourceManager, namespaces, lang);
        com.aetherianartificer.townstead.root.outfit.RootOutfits.setRoot(outfitStaging);
        outfitStaging = null;
        RootStates.replace(RootStates.Kind.ROOT, stateStaging);
        stateStaging = null;
        LOGGER.info("Loaded {} roots", parsed.size());
    }

    private static Map<ResourceLocation, JsonElement> readLegacyOrigins(ResourceManager resourceManager) {
        Map<ResourceLocation, JsonElement> out = new LinkedHashMap<>();
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
                if (obj != null) out.put(file, obj);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse legacy origin {}: {}", location, ex.getMessage());
            }
        }
        return out;
    }

    // Allowed states parsed during one apply() pass, committed with the roots.
    private static Map<ResourceLocation, Map<ResourceLocation, Boolean>> stateStaging;

    // Outfits parsed during one apply() pass, committed with the roots.
    private static Map<ResourceLocation, Map<String, com.aetherianartificer.townstead.root.outfit.RootOutfits.Outfit>> outfitStaging;

    private static void parseRoot(ResourceLocation file, JsonObject obj, Map<String, String> lang,
            Map<ResourceLocation, Root> parsed, boolean currentSchema) {
        String ctx = file.toString();
        if (!com.aetherianartificer.townstead.data.ModGate.allows(obj)) return;
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
            com.aetherianartificer.townstead.culture.CulturalSpawnBias culturalSpawnBias = com.aetherianartificer.townstead.culture.CulturalSpawnBias.parse(obj);
            ResourceLocation kinFlesh = RootJsonParsing.optionalId(obj, "kin_flesh", ctx, LOGGER);
            boolean eatsSapients = net.minecraft.util.GsonHelper.getAsBoolean(obj, "eats_sapients", false);
            String icon = net.minecraft.util.GsonHelper.getAsString(obj, "icon", "");
            boolean featured = net.minecraft.util.GsonHelper.getAsBoolean(obj, "featured", false);
            parsed.put(file, new Root(file, displayName, species, ancestry, lineage, demonym, backstory, genome, spawnBias,
                    culturalSpawnBias,
                    kinFlesh, eatsSapients, icon, featured));
            if (outfitStaging != null) outfitStaging.put(file, com.aetherianartificer.townstead.root.outfit.RootOutfits.parse(obj));
            if (stateStaging != null) stateStaging.put(file, RootStates.parse(obj));
        } catch (Exception ex) {
            LOGGER.warn("Failed to parse root {}: {}", file, ex.getMessage());
        }
    }
}
