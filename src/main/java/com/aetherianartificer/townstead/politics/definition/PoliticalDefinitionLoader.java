package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads bonds and faction kinds together so a reload publishes one coherent set. Bonds come from
 * {@code bond/}, and from the older {@code bond_kind/} folder when {@code bond/} has no file of
 * the same id.
 */
public final class PoliticalDefinitionLoader
        extends SimplePreparableReloadListener<PoliticalDefinitionLoader.Prepared> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/PoliticalDefinitions");

    public record Prepared(Map<ResourceLocation, JsonObject> bonds,
                           Map<ResourceLocation, JsonObject> legacyBonds,
                           Map<ResourceLocation, JsonObject> kinds,
                           int legacyKinds) {}

    @Override
    protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new Prepared(read(resourceManager, "bond"), read(resourceManager, "bond_kind"),
                read(resourceManager, "faction"), read(resourceManager, "organization_kind").size());
    }

    private static Map<ResourceLocation, JsonObject> read(ResourceManager manager, String directory) {
        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        String prefix = directory + "/";
        for (Map.Entry<ResourceLocation, Resource> entry : manager
                .listResources(directory, id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath();
            ResourceLocation id = DataPackLang.parseId(file.getNamespace() + ":"
                    + path.substring(prefix.length(), path.length() - ".json".length()));
            if (id == null) continue;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                else LOGGER.warn("Political definition {} is not a JSON object", file);
            } catch (Exception error) {
                LOGGER.warn("Could not read political definition {}: {}", file, error.getMessage());
            }
        }
        return out;
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        List<Diagnostic> diagnostics = new ArrayList<>();
        Map<ResourceLocation, JsonObject> rawBonds = new LinkedHashMap<>(prepared.legacyBonds());
        rawBonds.putAll(prepared.bonds());
        Map<ResourceLocation, BondKind> bonds = new LinkedHashMap<>();
        rawBonds.forEach((id, json) -> {
            if (!enabled(id, json)) return;
            try {
                bonds.put(id, BondKind.parse(id, json, lang));
            } catch (RuntimeException error) {
                LOGGER.warn("Bond {} rejected: {}", id, error.getMessage());
                diagnostics.add(Diagnostic.error(id, "$", error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage()));
            }
        });
        BondKinds.replaceAll(bonds);
        PhenoDiagnostics.replace("bond_kind", diagnostics);

        Map<ResourceLocation, FactionKind> kinds = new LinkedHashMap<>();
        prepared.kinds().forEach((id, json) -> {
            if (!enabled(id, json)) return;
            try {
                FactionKind kind = FactionKind.parse(id, json, lang);
                List<String> errors = PoliticalDefinitions.validate(kind, bonds);
                if (!errors.isEmpty()) {
                    LOGGER.warn("Faction kind {} rejected: {}", id, String.join("; ", errors));
                    return;
                }
                kinds.put(id, kind);
            } catch (RuntimeException error) {
                LOGGER.warn("Faction kind {} rejected: {}", id, error.getMessage());
            }
        });
        PoliticalDefinitions.replace(kinds);
        if (prepared.legacyKinds() > 0) {
            LOGGER.warn("{} organization_kind files were ignored: faction kinds now live in faction/ with schema {}",
                    prepared.legacyKinds(), FactionKind.SCHEMA);
        }
        LOGGER.info("Loaded {} bonds and {} faction kinds", bonds.size(), kinds.size());
    }

    private static boolean enabled(ResourceLocation id, JsonObject json) {
        if (!json.has("mods")) return true;
        Boolean enabled = ModGate.evaluate(json.get("mods"));
        if (enabled == null) LOGGER.warn("Political definition {} rejected: malformed 'mods' gate", id);
        else if (!enabled) LOGGER.debug("Political definition {} skipped: required mods are unavailable", id);
        return Boolean.TRUE.equals(enabled);
    }
}
