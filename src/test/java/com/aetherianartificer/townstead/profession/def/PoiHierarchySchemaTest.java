package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A def's {@code poi} list may declare {@code via} on job-block entries — the (alias)
 * profession whose vanilla POI claim manifests that surface. Purely descriptive data:
 * acquisition itself is vanilla block logic, and via entries feed alias resolution, the
 * cook-site list, and the JEP consolidated entry.
 */
class PoiHierarchySchemaTest {

    @Test
    void viaParsesOnJobBlockEntries() {
        ProfessionDef def = parse("""
                {"poi": [
                  {"type": "townstead:building", "type_prefix": "compat/farmersdelight/kitchen_l"},
                  {"type": "townstead:job_block", "block": "farmersdelight:cooking_pot", "via": "chefsdelight:chef"},
                  {"type": "townstead:job_block", "block": "farmersdelight:skillet"}
                ]}""");
        assertEquals(3, def.jobSites().size());
        JobSiteProvider.JobBlock pot = (JobSiteProvider.JobBlock) def.jobSites().get(1);
        assertEquals(id("chefsdelight:chef"), pot.via());
        JobSiteProvider.JobBlock skillet = (JobSiteProvider.JobBlock) def.jobSites().get(2);
        assertNull(skillet.via(), "via is optional; a plain job block has no alias surface");
    }

    @Test
    void hierarchyPredicatesKeyOffVia() {
        ProfessionDef def = parse("""
                {"poi": [
                  {"type": "townstead:building", "type_prefix": "compat/farmersdelight/kitchen_l"},
                  {"type": "townstead:job_block", "block": "farmersdelight:cooking_pot", "via": "chefsdelight:chef"}
                ]}""");
        assertTrue(PoiHierarchy.hasAcquisitionHierarchy(def));
        assertTrue(PoiHierarchy.isAcquisitionSurface(def, id("chefsdelight:chef")));
        assertFalse(PoiHierarchy.isAcquisitionSurface(def, id("townstead:cook")),
                "the canonical id is not a subordinate surface; Townstead's own assignment paths pass");
        assertFalse(PoiHierarchy.isAcquisitionSurface(def, id("chefsdelight:cook")));
    }

    @Test
    void defWithoutViaHasNoHierarchy() {
        ProfessionDef def = parse("""
                {"poi": [
                  {"type": "townstead:job_block", "block": "minecraft:composter"}
                ]}""");
        assertFalse(PoiHierarchy.hasAcquisitionHierarchy(def),
                "plain job-block professions (farmer) are never acquisition-gated");
    }

    @Test
    void shippedCookDefDeclaresTheChefsDelightSurfaces() {
        ProfessionDef cook = load("/data/townstead/profession/cook/profession.json", "townstead:cook");
        assertTrue(PoiHierarchy.hasAcquisitionHierarchy(cook));
        assertTrue(PoiHierarchy.isAcquisitionSurface(cook, id("chefsdelight:chef")),
                "the cooking pot manifests through chefsdelight:chef");
        assertTrue(PoiHierarchy.isAcquisitionSurface(cook, id("chefsdelight:cook")),
                "the skillet manifests through chefsdelight:cook");
        assertTrue(cook.jobSites().get(0) instanceof JobSiteProvider.Building,
                "entry 0 stays the primary building surface");
        for (ResourceLocation via : new ResourceLocation[]{id("chefsdelight:chef"), id("chefsdelight:cook")}) {
            assertTrue(cook.aliases().contains(via),
                    via + " must also be a cook alias so the claim resolves to the canonical career");
        }
    }

    private static ProfessionDef parse(String json) {
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id("test:subject"));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id("test:subject"), JsonParser.parseString(json).getAsJsonObject(),
                Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(def);
        return def;
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static ProfessionDef load(String resource, String idRaw) {
        InputStream in = PoiHierarchySchemaTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        if ("townstead:cook".equals(idRaw)) {
            ProfessionPathDocument.apply(json, "pizzaiolo", readResource(
                    "/data/townstead/profession/cook/path/pizzaiolo/path.json"));
        }
        InputStream work = PoiHierarchySchemaTest.class.getResourceAsStream(
                resource.substring(0, resource.lastIndexOf('/') + 1) + "work.json");
        if (work != null) {
            ProfessionWorkOverlay.apply(json, JsonParser.parseReader(
                    new InputStreamReader(work, StandardCharsets.UTF_8)).getAsJsonObject());
        }
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id(idRaw));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id(idRaw), json, Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(def, resource + " must parse");
        return def;
    }

    private static JsonObject readResource(String resource) {
        InputStream in = PoiHierarchySchemaTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        return JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
