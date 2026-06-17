package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.origin.personality.Personalities;
import com.aetherianartificer.townstead.origin.personality.PersonalityPolicies;
import com.aetherianartificer.townstead.origin.personality.PersonalityPolicyRegistry;
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
        Map<ResourceLocation, Personalities> policies = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String ctx = file.toString();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), ctx);
                TownsteadSchema.validate(obj, "townstead:origin/v1");
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
                ResourceLocation species = OriginJsonParsing.optionalId(obj, "species", ctx, LOGGER);
                ResourceLocation ancestry = OriginJsonParsing.optionalId(obj, "ancestry", ctx, LOGGER);
                ResourceLocation lineage = OriginJsonParsing.optionalId(obj, "lineage", ctx, LOGGER);
                Demonym demonym = OriginJsonParsing.demonym(obj, ctx, lang);
                Component backstory = OriginJsonParsing.backstory(obj, ctx, lang);
                Genome genome = OriginJsonParsing.genes(obj, ctx, LOGGER);
                SpawnBias spawnBias = OriginJsonParsing.spawnBias(obj, ctx, LOGGER);
                parsed.put(file, new Origin(file, displayName, species, ancestry, lineage, demonym, backstory, genome, spawnBias));
                policies.put(file, PersonalityPolicies.parse(obj));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse origin {}: {}", file, ex.getMessage());
            }
        }
        OriginRegistry.replaceAll(parsed);
        PersonalityPolicyRegistry.setOrigin(policies);
        LOGGER.info("Loaded {} origins", parsed.size());
    }
}
