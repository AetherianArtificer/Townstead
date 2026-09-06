package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

/** Loads relationship interpretations after the shared Pheno vocabulary is registered. */
public final class RelationshipDescriptorJsonLoader extends SimpleJsonResourceReloadListener {
    public RelationshipDescriptorJsonLoader() { super(new Gson(), "relationship_descriptor"); }
    @Override protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(manager);
        List<RelationshipDescriptor> parsed = new ArrayList<>(); List<Diagnostic> diagnostics = new ArrayList<>();
        entries.forEach((id, element) -> {
            try { parsed.add(RelationshipDescriptor.parse(id, GsonHelper.convertToJsonObject(element, id.toString()), lang)); }
            catch (RuntimeException ex) {
                diagnostics.add(Diagnostic.error(id, "$", ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
                Townstead.LOGGER.warn("Relationship descriptor rejected: {} $.{}", id, ex.getMessage());
            }
        });
        RelationshipDescriptors.replaceAll(parsed); PhenoDiagnostics.replace("relationship_descriptor", diagnostics);
        Townstead.LOGGER.info("Loaded {} relationship descriptors ({} rejected)", parsed.size(), diagnostics.size());
    }
}
