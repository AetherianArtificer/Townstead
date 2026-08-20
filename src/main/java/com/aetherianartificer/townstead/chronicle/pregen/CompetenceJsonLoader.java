package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.Townstead;
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

/** Loads {@code data/<ns>/competence/*.json} into {@link CompetenceDefs}. */
public final class CompetenceJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/CompetenceJsonLoader");
    private static final Gson GSON = new Gson();

    public CompetenceJsonLoader() {
        super(GSON, "competence");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, Competence> parsed = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                parsed.put(file, Competence.parse(file,
                        GsonHelper.convertToJsonObject(entry.getValue(), file.toString())));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse competence {}: {}", file, ex.getMessage());
                diagnostics.add(Diagnostic.error(file, "$", ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        CompetenceDefs.replaceAll(parsed);
        PhenoDiagnostics.replace("competence", diagnostics);
        LOGGER.info("Loaded {} competence profiles", parsed.size());
    }
}
