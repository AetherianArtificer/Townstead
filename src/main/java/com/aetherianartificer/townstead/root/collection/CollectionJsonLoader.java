package com.aetherianartificer.townstead.root.collection;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.types.CollectionGeneType;
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
 * Loads {@code data/<ns>/collection/*.json} into {@link CollectionDefs}, using the
 * same parser as the {@code pheno:collection} gene so the config is identical
 * either way ({@code of}, {@code max}, {@code on_full}, {@code forget_after},
 * {@code on_add}/{@code on_remove}/{@code on_reach}).
 */
public final class CollectionJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/CollectionJsonLoader");
    private static final Gson GSON = new Gson();
    private static final CollectionGeneType PARSER = new CollectionGeneType();

    public CollectionJsonLoader() {
        super(GSON, "collection");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, CollectionGeneType.Instance> parsed = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                GeneInstance instance = PARSER.parse(
                        GsonHelper.convertToJsonObject(entry.getValue(), file.toString()));
                if (instance instanceof CollectionGeneType.Instance collection) {
                    parsed.put(file, collection);
                } else {
                    diagnostics.add(Diagnostic.error(file, "$", "not a valid collection declaration"));
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse collection {}: {}", file, ex.getMessage());
                diagnostics.add(Diagnostic.error(file, "$", ex.getMessage() == null
                        ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        CollectionDefs.replaceAll(parsed);
        PhenoDiagnostics.replace("collection", diagnostics);
        LOGGER.info("Loaded {} gene-less collection declarations", parsed.size());
    }
}
