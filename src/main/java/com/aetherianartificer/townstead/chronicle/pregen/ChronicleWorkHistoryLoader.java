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

/** Loads {@code data/<ns>/chronicle_work_history/*.json}. */
public final class ChronicleWorkHistoryLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/ChronicleWorkHistoryLoader");
    private static final Gson GSON = new Gson();

    public ChronicleWorkHistoryLoader() {
        super(GSON, "chronicle_work_history");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, ChronicleWorkHistory> parsed = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                parsed.put(file, ChronicleWorkHistory.parse(file,
                        GsonHelper.convertToJsonObject(entry.getValue(), file.toString())));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse Chronicle work history {}: {}", file, ex.getMessage());
                diagnostics.add(Diagnostic.error(file, "$", ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        ChronicleWorkHistories.replaceAll(parsed);
        PhenoDiagnostics.replace("chronicle_work_history", diagnostics);
        LOGGER.info("Loaded {} Chronicle work-history profiles", parsed.size());
    }
}
