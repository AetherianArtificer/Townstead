package com.aetherianartificer.townstead.origin.gene;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.aetherianartificer.townstead.pheno.lang.validate.PhenoValidator;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
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
import java.util.Optional;

/**
 * Loads {@link Gene}s from {@code data/<ns>/gene/*.json}. Each file names a
 * {@link GeneType} via {@code "type"}; the type parses its own config. Common
 * fields ({@code display_name}, {@code description}, {@code category}) are parsed
 * here. Unknown/invalid types are skipped with a warning.
 */
public final class GeneJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/GeneJsonLoader");
    private static final Gson GSON = new Gson();

    public GeneJsonLoader() {
        super(GSON, "gene");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Gene> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, List<ResourceLocation>> companions = new LinkedHashMap<>();
        Diagnostics diagnostics = new Diagnostics();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                obj = PhenoNormalizer.normalize(obj);
                PhenoValidator.validateGene(file, obj, diagnostics);
                Map<ResourceLocation, JsonObject> companionConfigs = GeneCompanions.extract(file, obj);
                String typeKey = GsonHelper.getAsString(obj, "type", "");
                Optional<GeneType> type = GeneTypes.get(typeKey);
                if (type.isEmpty()) {
                    LOGGER.warn("Skipping gene {} — unknown type '{}'", file, typeKey);
                    continue;
                }
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), file.toString(), lang);
                Component description = obj.has("description")
                        ? DataPackLang.parseComponent(obj.get("description"), file + ".description", lang)
                        : null;
                String category = GsonHelper.getAsString(obj, "category", "general");
                Dominance dominance = Dominance.fromString(GsonHelper.getAsString(obj, "dominance", "dominant"));
                ResourceLocation locus = obj.has("locus")
                        ? DataPackLang.parseId(GsonHelper.getAsString(obj, "locus", ""))
                        : null;
                int weight = Math.max(1, GsonHelper.getAsInt(obj, "weight", 1));

                List<GeneVariant> variants = parseVariants(file, obj, type.get(), displayName, weight, lang);
                if (variants.isEmpty()) {
                    LOGGER.warn("Skipping gene {} — invalid config for type '{}'", file, typeKey);
                    continue;
                }
                if (locus == null) {
                    locus = type.get().defaultLocus(variants.get(0).instance());
                }
                parsed.put(file, new Gene(file, displayName, description, category,
                        dominance, locus, weight, variants));
                registerCompanions(file, companionConfigs, lang, parsed, companions);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse gene {}: {}", file, ex.getMessage());
            }
        }
        GeneRegistry.replaceAll(parsed, companions);
        PhenoDiagnostics.replace("gene", diagnostics.all());
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) LOGGER.warn("pheno: {}", d.render());
        }
        int errors = diagnostics.count(Severity.ERROR);
        LOGGER.info("Loaded {} genes ({} pheno diagnostic{})",
                parsed.size(), diagnostics.all().size(), diagnostics.all().size() == 1 ? "" : "s");
        if (errors > 0) LOGGER.warn("pheno: {} error diagnostic(s); run /pheno validate for detail", errors);
    }

    /**
     * Register a gene's inline companion resources as real {@code pheno:resource} genes keyed by
     * the derived id {@code <parentId>/<name>}, and record the parent->companions link so they
     * ride along the parent's expression. Skipped silently when a config is invalid.
     */
    private static void registerCompanions(ResourceLocation parent, Map<ResourceLocation, JsonObject> configs,
                                           Map<String, String> lang, Map<ResourceLocation, Gene> parsed,
                                           Map<ResourceLocation, List<ResourceLocation>> companions) {
        if (configs.isEmpty()) return;
        List<ResourceLocation> ids = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : configs.entrySet()) {
            ResourceLocation id = e.getKey();
            JsonObject config = e.getValue();
            Optional<GeneType> type = GeneTypes.get(GsonHelper.getAsString(config, "type", ""));
            if (type.isEmpty()) {
                LOGGER.warn("Gene {} — companion resource '{}' has unknown type, skipping", parent, id);
                continue;
            }
            GeneInstance instance = type.get().parse(config, lang);
            if (instance == null) {
                LOGGER.warn("Gene {} — companion resource '{}' has invalid config, skipping", parent, id);
                continue;
            }
            String shortName = id.getPath().substring(id.getPath().lastIndexOf('/') + 1);
            Component name = Component.literal(shortName);
            ResourceLocation locus = type.get().defaultLocus(instance);
            parsed.put(id, new Gene(id, name, null, "resource", Dominance.fromString("recessive"),
                    locus, 1, List.of(new GeneVariant(shortName, name, 1, instance))));
            ids.add(id);
        }
        if (!ids.isEmpty()) companions.put(parent, ids);
    }

    /**
     * Parse a gene's variants. A {@code variants} object (keyed by variant id) means a
     * weighted pick-one gene: each entry carries a {@code weight}, an optional {@code label},
     * and either its own type config or a bare reference resolved by the type (e.g. a shared
     * catalog window). Otherwise the whole object is one implicit variant whose config is read
     * from the top level — preserving the simple single-value form.
     */
    private static List<GeneVariant> parseVariants(ResourceLocation file, JsonObject obj, GeneType type,
                                                   Component displayName, int geneWeight, Map<String, String> lang) {
        List<GeneVariant> variants = new ArrayList<>();
        if (obj.has("variants") && obj.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : obj.getAsJsonObject("variants").entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                String id = e.getKey();
                JsonObject vo = e.getValue().getAsJsonObject();
                GeneInstance instance = type.parseVariant(id, vo, lang);
                if (instance == null) {
                    LOGGER.warn("Gene {} — variant '{}' has invalid config, skipping", file, id);
                    continue;
                }
                Component variantName;
                if (vo.has("display_name")) {
                    variantName = DataPackLang.parseComponent(vo.get("display_name"), file + ".variant." + id, lang);
                } else {
                    Component fallback = type.variantLabel(id);
                    variantName = fallback != null ? fallback : displayName;
                }
                int weight = Math.max(1, GsonHelper.getAsInt(vo, "weight", geneWeight));
                variants.add(new GeneVariant(id, variantName, weight, instance));
            }
        } else {
            GeneInstance instance = type.parse(obj, lang);
            if (instance != null) {
                variants.add(new GeneVariant(file.getPath(), displayName, geneWeight, instance));
            }
        }
        return variants;
    }
}
