package com.aetherianartificer.townstead.farming.pattern;

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

import java.util.List;
import java.util.Map;

public final class FarmPatternDataLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/FarmPatternDataLoader");
    private static final Gson GSON = new GsonBuilder().create();
    private static final String DIRECTORY = "townstead/farm_patterns";

    public FarmPatternDataLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        FarmPatternRegistry.bootstrap();
        FarmPatternRegistry.clearDatapackPatterns();

        int loaded = 0;
        int rejected = 0;
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation location = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), "farm pattern");
                String id = GsonHelper.getAsString(json, "id", location.getPath()).trim();
                String plannerType = GsonHelper.getAsString(json, "plannerType", id).trim();
                if (plannerType.isEmpty()) plannerType = id;
                int level = Math.max(1, Math.min(5, GsonHelper.getAsInt(json, "level", GsonHelper.getAsInt(json, "tier", 1))));
                int requiredTier = Math.max(1, Math.min(5, GsonHelper.getAsInt(json, "requiredTier", level)));
                String family = GsonHelper.getAsString(json, "family", deriveFamilyFromId(id)).trim();
                if (family.isEmpty()) family = deriveFamilyFromId(id);
                List<String> schemaErrors = FarmPatternSchemaValidator.validate(json);
                if (!schemaErrors.isEmpty()) {
                    rejected++;
                    LOGGER.warn("Rejected farm pattern '{}': {}", location, String.join("; ", schemaErrors));
                    continue;
                }
                String requiredMod = GsonHelper.getAsString(json, "required_mod", "").trim();
                if (FarmPatternRegistry.register(new FarmPatternDefinition(id, plannerType, requiredTier, family, level, requiredMod), false)) {
                    loaded++;
                } else {
                    rejected++;
                }
            } catch (Exception ex) {
                rejected++;
                LOGGER.warn("Rejected farm pattern '{}': {}", location, ex.getMessage());
            }
        }

        LOGGER.info(
                "Farm pattern datapack reload complete: loaded={}, rejected={}, total={}",
                loaded,
                rejected,
                entries.size()
        );
    }

    private static String deriveFamilyFromId(String id) {
        if (id == null || id.isBlank()) return FarmPatternRegistry.DEFAULT_PATTERN_ID;
        String normalized = id.trim();
        int idx = normalized.lastIndexOf("_l");
        if (idx > 0 && idx + 2 < normalized.length()) {
            String suffix = normalized.substring(idx + 2);
            boolean numeric = true;
            for (int i = 0; i < suffix.length(); i++) {
                if (!Character.isDigit(suffix.charAt(i))) {
                    numeric = false;
                    break;
                }
            }
            if (numeric) return normalized.substring(0, idx);
        }
        return normalized;
    }
}
