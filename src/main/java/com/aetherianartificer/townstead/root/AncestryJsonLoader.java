package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.root.personality.Personalities;
import com.aetherianartificer.townstead.root.personality.PersonalityPolicies;
import com.aetherianartificer.townstead.root.personality.PersonalityPolicyRegistry;
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

/** Loads {@link Ancestry} from {@code data/<ns>/ancestry/*.json}. */
public final class AncestryJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/AncestryJsonLoader");
    private static final Gson GSON = new Gson();

    public AncestryJsonLoader() {
        super(GSON, "ancestry");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Ancestry> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, Personalities> policies = new LinkedHashMap<>();
        Map<ResourceLocation, com.aetherianartificer.townstead.root.appearance.HairPolicy> hairPolicies = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String ctx = file.toString();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), ctx);
                TownsteadSchema.validate(obj, "townstead:ancestry/v1");
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), ctx, lang);
                ResourceLocation species = RootJsonParsing.optionalId(obj, "species", ctx, LOGGER);
                Demonym demonym = RootJsonParsing.demonym(obj, ctx, lang);
                Component backstory = RootJsonParsing.backstory(obj, ctx, lang);
                Genome genome = RootJsonParsing.genes(obj, ctx, LOGGER);
                SpawnBias spawnBias = RootJsonParsing.spawnBias(obj, ctx, LOGGER);
                com.aetherianartificer.townstead.culture.CulturalSpawnBias culturalSpawnBias = com.aetherianartificer.townstead.culture.CulturalSpawnBias.parse(obj);
                parsed.put(file, new Ancestry(file, displayName, species, demonym, backstory, genome, spawnBias,
                        culturalSpawnBias));
                policies.put(file, PersonalityPolicies.parse(obj));
                hairPolicies.put(file, com.aetherianartificer.townstead.root.appearance.HairPolicy.parse(obj, lang));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse ancestry {}: {}", file, ex.getMessage());
            }
        }
        AncestryRegistry.replaceAll(parsed);
        PersonalityPolicyRegistry.setAncestry(policies);
        com.aetherianartificer.townstead.root.appearance.HairPolicyRegistry.setAncestry(hairPolicies);
        LOGGER.info("Loaded {} origin ancestries", parsed.size());
    }
}
