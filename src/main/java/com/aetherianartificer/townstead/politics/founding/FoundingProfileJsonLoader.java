package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.LegacyIds;
import com.aetherianartificer.townstead.data.ModGate;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads {@code data/<namespace>/founding_profile/<path>.json}. */
public final class FoundingProfileJsonLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/FoundingProfiles");
    private static final Gson GSON = new Gson();

    public FoundingProfileJsonLoader() {
        super(GSON, "founding_profile");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resources);
        Map<ResourceLocation, FoundingProfileDefinition> loaded = new LinkedHashMap<>();
        Map<ResourceLocation, ResourceLocation> legacyIds = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), id.toString());
                if (json.has("mods")) {
                    Boolean enabled = ModGate.evaluate(json.get("mods"));
                    if (enabled == null) throw new IllegalArgumentException("malformed 'mods' gate");
                    if (!enabled) continue;
                }
                FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id, json, lang);
                List<String> errors = FoundingProfiles.validate(profile);
                if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
                var aliases = LegacyIds.parse(json, id);
                for (ResourceLocation legacyId : aliases) {
                    ResourceLocation previous = legacyIds.get(legacyId);
                    if (previous != null && !previous.equals(id)) {
                        throw new IllegalArgumentException("legacy id " + legacyId
                                + " is already claimed by " + previous);
                    }
                }
                loaded.put(id, profile);
                for (ResourceLocation legacyId : aliases) legacyIds.put(legacyId, id);
            } catch (RuntimeException error) {
                LOGGER.warn("Founding profile {} rejected: {}", id, error.getMessage());
            }
        }
        for (ResourceLocation canonical : loaded.keySet()) {
            ResourceLocation claimant = legacyIds.remove(canonical);
            if (claimant != null) {
                LOGGER.warn("Founding profile {} cannot be a legacy id for {}; the canonical definition wins",
                        canonical, claimant);
            }
        }
        FoundingProfiles.replace(loaded, legacyIds);
        LOGGER.info("Loaded {} founding profile(s)", loaded.size());
    }
}
