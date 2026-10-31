package com.aetherianartificer.townstead.root;

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
        entries = com.aetherianartificer.townstead.data.DataDocuments.resolve("heritage", entries, resourceManager);
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        com.aetherianartificer.townstead.data.NameCollisions.qualify("heritage", entries, resourceManager, lang);
        Map<ResourceLocation, HeritageProfile> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, com.aetherianartificer.townstead.root.appearance.HairPolicy> hairPolicies = new LinkedHashMap<>();
        Map<ResourceLocation, com.aetherianartificer.townstead.clothing.BodyClothing> bodyClothing = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String ctx = file.toString();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), ctx);
                TownsteadSchema.validate(obj, "townstead:heritage/v1");
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
                Demonym demonym = RootJsonParsing.demonym(obj, ctx, lang);
                Component backstory = RootJsonParsing.backstory(obj, ctx, lang);
                int priority = GsonHelper.getAsInt(obj, "priority", 0);
                Map<ResourceLocation, GeneRange> match = parseBands(obj, "match", ctx);
                Map<ResourceLocation, GeneRange> lineages = parseBands(obj, "lineages", ctx);
                if (match.isEmpty() && lineages.isEmpty()) {
                    LOGGER.warn("Skipping heritage {} — needs a 'match' or 'lineages'", file);
                    continue;
                }
                java.util.List<ResourceLocation> genes = new java.util.ArrayList<>();
                if (obj.has("genes") && obj.get("genes").isJsonArray()) {
                    for (JsonElement g : obj.getAsJsonArray("genes")) {
                        ResourceLocation gene = g.isJsonPrimitive() ? DataPackLang.parseId(g.getAsString()) : null;
                        if (gene == null) LOGGER.warn("{} — gene '{}' is not a valid id, skipping", ctx, g);
                        else genes.add(gene);
                    }
                }
                parsed.put(file, new HeritageProfile(file, displayName, demonym, backstory, priority, match, lineages, genes,
                        GsonHelper.getAsBoolean(obj, "open_lineages", false)));
                hairPolicies.put(file, com.aetherianartificer.townstead.root.appearance.HairPolicy.parse(obj, lang));
                bodyClothing.put(file, com.aetherianartificer.townstead.clothing.BodyClothing.parse(obj));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse heritage {}: {}", file, ex.getMessage());
            }
        }
        HeritageRegistry.replaceAll(parsed);
        com.aetherianartificer.townstead.root.appearance.HairPolicyRegistry.setHeritage(hairPolicies);
        com.aetherianartificer.townstead.clothing.BodyClothingRegistry.setHeritage(bodyClothing);
        LOGGER.info("Loaded {} heritage profiles", parsed.size());
    }

    /**
     * {@code "match": { "<ancestry>": {min,max} | <fraction> }} or {@code "lineages": { "<lineage>": … }};
     * a bare number is an exact share.
     */
    private static Map<ResourceLocation, GeneRange> parseBands(JsonObject obj, String field, String ctx) {
        Map<ResourceLocation, GeneRange> out = new LinkedHashMap<>();
        if (!obj.has(field) || !obj.get(field).isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject(field).entrySet()) {
            ResourceLocation id = DataPackLang.parseId(e.getKey());
            if (id == null) {
                LOGGER.warn("{} — {} id '{}' is not valid, skipping", ctx, field, e.getKey());
                continue;
            }
            if (e.getValue().isJsonObject()) {
                JsonObject band = e.getValue().getAsJsonObject();
                out.put(id, new GeneRange(GsonHelper.getAsFloat(band, "min", 0f),
                        GsonHelper.getAsFloat(band, "max", 1f)));
            } else if (e.getValue().isJsonPrimitive()) {
                float exact = e.getValue().getAsFloat();
                out.put(id, new GeneRange(exact, exact));
            }
        }
        return out;
    }
}
