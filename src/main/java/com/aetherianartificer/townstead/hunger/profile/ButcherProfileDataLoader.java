package com.aetherianartificer.townstead.hunger.profile;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ButcherProfileDataLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ButcherProfileDataLoader");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "townstead/butcher_profiles";

    public ButcherProfileDataLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        ButcherProfileRegistry.bootstrap();
        ButcherProfileRegistry.clearDatapackProfiles();

        int loaded = 0;
        int rejected = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "butcher profile");
                List<String> errors = ButcherProfileSchemaValidator.validate(json);
                if (!errors.isEmpty()) {
                    rejected++;
                    LOGGER.warn("Rejected butcher profile '{}': {}", location, String.join("; ", errors));
                    continue;
                }

                String id = GsonHelper.getAsString(json, "id", location.getPath()).trim();
                String family = GsonHelper.getAsString(json, "family", ButcherProfileSchemaValidator.deriveFamilyFromId(id)).trim();
                int level = Math.max(1, Math.min(5, GsonHelper.getAsInt(json, "level", GsonHelper.getAsInt(json, "required_tier", 1))));
                int requiredTier = Math.max(1, Math.min(5, GsonHelper.getAsInt(json, "required_tier", level)));

                JsonObject inputFilters = GsonHelper.getAsJsonObject(json, "input_filters");
                JsonObject fuelFilters = GsonHelper.getAsJsonObject(json, "fuel_filters");
                JsonObject outputRules = GsonHelper.getAsJsonObject(json, "output_rules");
                JsonObject throughputModifiers = GsonHelper.getAsJsonObject(json, "throughput_modifiers");

                Set<ResourceLocation> inputItems = readResourceSet(inputFilters, "items");
                Set<ResourceLocation> inputTags = readResourceSet(inputFilters, "tags");
                Set<ResourceLocation> fuelItems = readResourceSet(fuelFilters, "items");
                Set<ResourceLocation> fuelTags = readResourceSet(fuelFilters, "tags");
                Set<ResourceLocation> outputItems = readResourceSet(outputRules, "items");

                double throughputMod = Math.max(0.1d, GsonHelper.getAsDouble(throughputModifiers, "smoke_wait_scale", 1.0d));
                double stockCadenceMod = Math.max(0.1d, GsonHelper.getAsDouble(throughputModifiers, "stock_interval_scale", 1.0d));
                double requestIntervalMod = Math.max(0.1d, GsonHelper.getAsDouble(throughputModifiers, "request_interval_scale", 1.0d));

                ButcherProfileDefinition profile = new ButcherProfileDefinition(
                        id,
                        family,
                        level,
                        requiredTier,
                        ButcherProfileDefinition.normalizeSet(inputItems),
                        ButcherProfileDefinition.normalizeSet(inputTags),
                        ButcherProfileDefinition.normalizeSet(fuelItems),
                        ButcherProfileDefinition.normalizeSet(fuelTags),
                        ButcherProfileDefinition.normalizeSet(outputItems),
                        throughputMod,
                        stockCadenceMod,
                        requestIntervalMod
                );
                if (ButcherProfileRegistry.register(profile, false)) {
                    loaded++;
                } else {
                    rejected++;
                }
            } catch (Exception ex) {
                rejected++;
                LOGGER.warn("Rejected butcher profile '{}': {}", location, ex.getMessage());
            }
        }

        LOGGER.info(
                "Butcher profile datapack reload complete: loaded={}, rejected={}, total={}",
                loaded,
                rejected,
                entries.size()
        );
        for (ButcherProfileDefinition profile : ButcherProfileRegistry.all()) {
            LOGGER.debug(
                    "Butcher profile active: id={}, family={}, level={}, requiredTier={}",
                    profile.id(),
                    profile.family(),
                    profile.level(),
                    profile.requiredTier()
            );
        }
    }

    private static Set<ResourceLocation> readResourceSet(JsonObject parent, String key) {
        Set<ResourceLocation> set = new LinkedHashSet<>();
        if (parent == null) return set;
        for (JsonElement element : GsonHelper.getAsJsonArray(parent, key, new com.google.gson.JsonArray())) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) continue;
            String raw = element.getAsString().trim();
            if (raw.isEmpty()) continue;
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed != null) set.add(parsed);
        }
        return set;
    }
}
