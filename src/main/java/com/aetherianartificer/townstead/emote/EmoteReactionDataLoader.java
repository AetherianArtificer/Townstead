package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmoteReactionDataLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/EmoteReactionDataLoader");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "townstead/emote_reactions";

    public EmoteReactionDataLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        EmoteReactionRegistry.clear();

        int loaded = 0;
        int rejected = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "emote reaction");
                List<String> errors = EmoteReactionSchemaValidator.validate(json);
                if (!errors.isEmpty()) {
                    rejected++;
                    LOGGER.warn("Rejected emote reaction '{}': {}", location, String.join("; ", errors));
                    continue;
                }

                String id = GsonHelper.getAsString(json, "id", location.getPath()).trim();
                Set<String> triggers = new LinkedHashSet<>();
                JsonArray triggerArray = EmoteReactionSchemaValidator.readTriggers(json);
                for (JsonElement element : triggerArray) {
                    triggers.add(EmoteReactionRegistry.normalize(element.getAsString()));
                }

                List<EmoteReactionCandidate> candidates = new ArrayList<>();
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "candidates")) {
                    JsonObject candidate = element.getAsJsonObject();
                    EnumMap<Personality, Integer> personalityWeights = new EnumMap<>(Personality.class);
                    JsonObject personalityJson = GsonHelper.getAsJsonObject(candidate, "personality_weights", new JsonObject());
                    for (String key : personalityJson.keySet()) {
                        personalityWeights.put(
                                Personality.valueOf(key.trim().toUpperCase(java.util.Locale.ROOT)),
                                personalityJson.get(key).getAsInt()
                        );
                    }
                    candidates.add(new EmoteReactionCandidate(
                            GsonHelper.getAsString(candidate, "villager_emote", "").trim(),
                            GsonHelper.getAsString(candidate, "chat_key_prefix", "").trim(),
                            GsonHelper.getAsInt(candidate, "chat_variants", 0),
                            GsonHelper.getAsDouble(candidate, "chat_chance", 0.35d),
                            GsonHelper.getAsInt(candidate, "weight", 1),
                            personalityWeights
                    ));
                }

                if (EmoteReactionRegistry.register(new EmoteReactionDefinition(
                        id,
                        triggers,
                        GsonHelper.getAsString(json, "required_mod", "").trim(),
                        GsonHelper.getAsInt(json, "radius", 8),
                        GsonHelper.getAsInt(json, "cooldown_ticks", 200),
                        candidates
                ))) {
                    loaded++;
                } else {
                    rejected++;
                }
            } catch (Exception ex) {
                rejected++;
                LOGGER.warn("Rejected emote reaction '{}': {}", location, ex.getMessage());
            }
        }

        LOGGER.info("Emote reaction datapack reload complete: loaded={}, rejected={}, total={}", loaded, rejected, entries.size());
    }
}
