package com.aetherianartificer.townstead.chronicle.template;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
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

/**
 * Loads chronicle event templates from {@code data/<ns>/chronicle_event/*.json}
 * (schema {@code townstead:chronicle_event/v1}). One bad file warns and is
 * skipped; findings surface through the pheno diagnostics ({@code /pheno validate}).
 */
public final class ChronicleEventJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ChronicleEventJsonLoader");
    private static final Gson GSON = new Gson();

    public ChronicleEventJsonLoader() {
        super(GSON, "chronicle_event");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, ChronicleEventTemplate> parsed = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                var obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                TownsteadSchema.validate(obj, "townstead:chronicle_event/v1");
                ChronicleEventTemplate template = ChronicleEventTemplate.parse(file, obj, lang);
                parsed.put(file, template);
                for (String param : template.unfillablePregenParams()) {
                    diagnostics.add(Diagnostic.warning(file, "$.display.params",
                            "pre-history cannot fill display param '" + param + "'",
                            "no pregen role carries it, so the fabricated headline renders blank"));
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse chronicle_event {}: {}", file, ex.getMessage());
                diagnostics.add(Diagnostic.error(file, "$", ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        ChronicleEventRegistry.replaceAll(parsed);
        PhenoDiagnostics.replace("chronicle_event", diagnostics);
        LOGGER.info("Loaded {} chronicle event templates", parsed.size());
    }
}
