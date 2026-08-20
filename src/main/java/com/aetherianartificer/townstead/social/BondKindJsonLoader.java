package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads {@code data/<ns>/bond_kind/*.json} into {@link BondKinds}. */
public final class BondKindJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/BondKindJsonLoader");
    private static final Gson GSON = new Gson();

    public BondKindJsonLoader() {
        super(GSON, "bond_kind");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, BondKind> parsed = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                parsed.put(file, BondKind.parse(file,
                        GsonHelper.convertToJsonObject(entry.getValue(), file.toString()), lang));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse bond_kind {}: {}", file, ex.getMessage());
                diagnostics.add(Diagnostic.error(file, "$", ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        BondKinds.replaceAll(parsed);
        PhenoDiagnostics.replace("bond_kind", diagnostics);
        LOGGER.info("Loaded {} bond kinds", parsed.size());
    }
}
