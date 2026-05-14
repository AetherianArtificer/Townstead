package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.reaction.trigger.TriggerInstance;
import com.aetherianartificer.townstead.reaction.trigger.TriggerType;
import com.aetherianartificer.townstead.reaction.trigger.TriggerTypes;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Loads reaction JSON files from {@code data/<ns>/townstead/reactions/}
 * and publishes them into {@link ReactionRegistry}. Unlike vanilla's
 * {@code SimpleJsonResourceReloadListener} (which gives us only the
 * highest-priority pack's view), this loader walks the full per-id
 * resource stack so multiple packs targeting the same reaction merge
 * additively.
 *
 * <p>Merge semantics:</p>
 * <ul>
 *   <li>{@code bindings} / {@code triggers} / {@code tags} concatenate
 *       across packs (low → high priority) and dedup by full content
 *       equality.
 *   <li>Scalars ({@code cooldown_ticks}, {@code chance}, etc.) take the
 *       value from the highest-priority pack that set them.
 *   <li>A pack can set {@code "replace": true} to drop all prior
 *       contributions and start the merge fresh from itself.
 * </ul>
 */
public final class ReactionDataLoader implements PreparableReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "townstead/reactions";
    private static final String SUFFIX = ".json";

    public ReactionDataLoader() {}

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
            ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
            Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> prepare(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(this::apply, gameExecutor);
    }

    private record Prepared(Map<ResourceLocation, Reaction> reactions, TriggerIndex triggerIndex,
                            Map<String, Integer> triggerStats) {}

    private static Prepared prepare(ResourceManager resourceManager) {
        Map<ResourceLocation, List<Resource>> stacks = resourceManager.listResourceStacks(
                DIRECTORY, location -> location.getPath().endsWith(SUFFIX));

        Map<ResourceLocation, Reaction> merged = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : stacks.entrySet()) {
            ResourceLocation reactionId = stripPathToId(entry.getKey());
            if (reactionId == null) continue;
            Reaction accumulator = null;
            for (Resource resource : entry.getValue()) {
                Reaction contribution = readOne(reactionId, resource);
                if (contribution == null) continue;
                accumulator = Reaction.mergeFrom(accumulator, contribution);
            }
            if (accumulator == null) continue;
            if (accumulator.bindings().isEmpty()) {
                Townstead.LOGGER.warn("Reaction '{}' has no usable bindings after merge; will never fire", reactionId);
            }
            merged.put(reactionId, accumulator);
        }

        TriggerIndex.Builder indexBuilder = TriggerIndex.builder();
        Map<String, Integer> triggerStats = new HashMap<>();
        for (Map.Entry<ResourceLocation, Reaction> entry : merged.entrySet()) {
            for (JsonObject raw : entry.getValue().rawTriggers()) {
                String type = GsonHelper.getAsString(raw, "type", "");
                if (type.isEmpty()) continue;
                Optional<TriggerType> triggerType = TriggerTypes.get(type);
                if (triggerType.isEmpty()) {
                    Townstead.LOGGER.warn("Reaction '{}' references unknown trigger type '{}'; ignored",
                            entry.getKey(), type);
                    continue;
                }
                TriggerInstance instance = triggerType.get().parse(raw);
                if (instance == null) {
                    Townstead.LOGGER.warn("Reaction '{}' has malformed '{}' trigger; ignored",
                            entry.getKey(), type);
                    continue;
                }
                triggerType.get().index(instance, entry.getKey(), indexBuilder);
                triggerStats.merge(type, 1, Integer::sum);
            }
        }

        return new Prepared(merged, indexBuilder.build(), triggerStats);
    }

    private static Reaction readOne(ResourceLocation reactionId, Resource resource) {
        try (InputStream in = resource.open();
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonElement element = GSON.fromJson(reader, JsonElement.class);
            JsonObject json = GsonHelper.convertToJsonObject(element, "reaction");
            return Reaction.parse(reactionId, json);
        } catch (Exception ex) {
            Townstead.LOGGER.warn("Rejected reaction contribution '{}': {}", reactionId, ex.getMessage());
            return null;
        }
    }

    private static ResourceLocation stripPathToId(ResourceLocation full) {
        String path = full.getPath();
        if (!path.startsWith(DIRECTORY + "/") || !path.endsWith(SUFFIX)) return null;
        String stripped = path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
        //? if neoforge {
        return ResourceLocation.fromNamespaceAndPath(full.getNamespace(), stripped);
        //?} else {
        /*return new ResourceLocation(full.getNamespace(), stripped);
        *///?}
    }

    private void apply(Prepared prepared) {
        ReactionRegistry.replaceAll(prepared.reactions(), prepared.triggerIndex());

        if (Townstead.LOGGER.isInfoEnabled()) {
            StringBuilder trigSummary = new StringBuilder();
            for (Map.Entry<String, Integer> e : prepared.triggerStats().entrySet()) {
                if (trigSummary.length() > 0) trigSummary.append(", ");
                trigSummary.append(e.getKey()).append('=').append(e.getValue());
            }
            Townstead.LOGGER.info("Townstead reactions loaded {} (triggers: {})",
                    prepared.reactions().size(),
                    trigSummary.length() == 0 ? "none" : trigSummary);
        }
    }
}
