package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.aetherianartificer.townstead.reaction.trigger.TriggerTypes;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads reaction JSON files from {@code data/<ns>/townstead/reactions/}
 * and publishes them into {@link ReactionRegistry}. Mirrors the existing
 * {@link com.aetherianartificer.townstead.client.catalog.CatalogDataLoader}
 * style (plain Gson, no codecs) for consistency. Files that fail to parse
 * are logged and skipped; the rest still load.
 */
public final class ReactionDataLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "townstead/reactions";

    public ReactionDataLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
            ProfilerFiller profiler) {
        Map<ResourceLocation, Reaction> next = new LinkedHashMap<>();
        Map<String, Integer> triggerStats = new HashMap<>();
        TriggerIndex.Builder indexBuilder = TriggerIndex.builder();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "reaction");
                Reaction reaction = Reaction.parse(location, json);
                next.put(location, reaction);
                for (JsonObject raw : reaction.rawTriggers()) {
                    String type = GsonHelper.getAsString(raw, "type", "");
                    if (type.isEmpty()) continue;
                    Optional<TriggerType> triggerType = TriggerTypes.get(type);
                    if (triggerType.isEmpty()) {
                        Townstead.LOGGER.warn("Reaction '{}' references unknown trigger type '{}'; ignored",
                                location, type);
                        continue;
                    }
                    TriggerInstance instance = triggerType.get().parse(raw);
                    if (instance == null) {
                        Townstead.LOGGER.warn("Reaction '{}' has malformed '{}' trigger; ignored", location, type);
                        continue;
                    }
                    triggerType.get().index(instance, location, indexBuilder);
                    triggerStats.merge(type, 1, Integer::sum);
                }
            } catch (Exception ex) {
                Townstead.LOGGER.warn("Rejected reaction '{}': {}", location, ex.getMessage());
            }
        }

        ReactionRegistry.replaceAll(next, indexBuilder.build());

        if (Townstead.LOGGER.isInfoEnabled()) {
            StringBuilder trigSummary = new StringBuilder();
            for (Map.Entry<String, Integer> e : triggerStats.entrySet()) {
                if (trigSummary.length() > 0) trigSummary.append(", ");
                trigSummary.append(e.getKey()).append('=').append(e.getValue());
            }
            Townstead.LOGGER.info("Townstead reactions loaded {} (triggers: {})", next.size(),
                    trigSummary.length() == 0 ? "none" : trigSummary);
        }
    }
}
