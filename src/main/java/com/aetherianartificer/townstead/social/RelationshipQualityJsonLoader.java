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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads public relationship qualities from data/&lt;namespace&gt;/relationship_quality. */
public final class RelationshipQualityJsonLoader extends SimpleJsonResourceReloadListener {
    public RelationshipQualityJsonLoader() { super(new Gson(), "relationship_quality"); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(manager);
        Map<ResourceLocation, RelationshipQuality> parsed = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        entries.forEach((id, element) -> {
            try { parsed.put(id, RelationshipQuality.parse(id, GsonHelper.convertToJsonObject(element, id.toString()), lang)); }
            catch (RuntimeException ex) {
                diagnostics.add(Diagnostic.error(id, "$", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                Townstead.LOGGER.warn("Relationship quality rejected: {} $.{}", id, ex.getMessage());
            }
        });
        RelationshipQualities.replaceAll(parsed);
        PhenoDiagnostics.replace("relationship_quality", diagnostics);
        Townstead.LOGGER.info("Loaded {} relationship qualities ({} rejected)", parsed.size(), diagnostics.size());
    }
}
