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
 * The v2 per-level schema: levels are the authoring unit, inline skills register with derived
 * ids and tiers, per-level trades land in the merchant map, and skill points accumulate per
 * level reached (v1 defs fall back to points_per_tier).
 */
class LevelSchemaTest {

    @Test
    void cookRunsFiveLevels() {
        Map<ResourceLocation, SkillDef> inline = new LinkedHashMap<>();
        ProfessionDef cook = loadCook(inline);

        // Five levels, five picks. The 22-level track was abandoned: it existed to give a points
        // economy something to spend on, and levels-and-options replaced the economy itself.
        assertEquals(5, cook.levels().size());
        assertEquals(5, cook.progression().maxTier());
        assertEquals(java.util.List.of(0, 110, 300, 660, 1250), cook.progression().tierThresholds(),
                "the original thresholds, unmoved, so a save that levelled against them is safe");
        assertEquals(230, cook.progression().dailyCap());

        assertTrue(inline.isEmpty(), "references never register defs; the profession's skill/ dir owns them");
        // Levels no longer carry skill pools. A skill's own `tier` says which level offers it,
        // and the profession lists its skills once, so there is a single place to change either.
        for (var level : cook.levels()) {
            assertTrue(level.skills().isEmpty(),
                    "a level's options come from the skills' tiers, not from a second list");
        }
        assertTrue(cook.skills().contains(id("townstead:cook/open_flame")),
                "referenced skills join the flat membership list");
        assertTrue(cook.skills().contains(id("townstead:cook/pizza_craft")),
                "path skills pool like any other skill; the path steers who buys them");
        assertTrue(cook.skills().contains(id("townstead:cook/pizza_spin")),
                "ability skills are ordinary skills; only their power block differs");
        // Three paths of ten options each (two per level, five levels) plus the two skills
        // belonging to no path, which compete for the same picks.
        assertEquals(32, cook.skills().size());
    }

    /** Cook's progression ships as a sidecar, merged here the same way {@code apply()} does. */
    private static ProfessionDef loadCook(Map<ResourceLocation, SkillDef> inlineOut) {
        JsonObject def = readResource("/data/townstead/profession/cook/profession.json");
        ProfessionDataLoader.applyLevelsOverlay(def,
                readResource("/data/townstead/profession/cook/levels.json"));
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id("townstead:cook"));
        ProfessionDef cook = ProfessionDataLoader.parseProfession(
                id("townstead:cook"), def, Map.of(), diagnostics, inlineOut);
        assertNotNull(cook, "cook must parse");
        return cook;
    }

    @Test
    void inlineSkillsStillRegisterWithDerivedIds() {
        JsonObject def = JsonParser.parseString("""
                {"schema": "townstead:profession/v2",
                 "levels": [
                   {"xp": 10},
                   {"xp": 20, "skills": [
                     {"id": "quick_study", "cost": 2,
                      "exclusive_with": ["test:slow_study"]}]}]}""").getAsJsonObject();
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id("test:tutor"));
        Map<ResourceLocation, SkillDef> inline = new LinkedHashMap<>();
        ProfessionDef tutor = ProfessionDataLoader.parseProfession(
                id("test:tutor"), def, Map.of(), diagnostics, inline);
        assertNotNull(tutor);
        SkillDef quickStudy = inline.get(id("test:tutor/quick_study"));
        assertNotNull(quickStudy, "inline skills register under the profession's path");
        assertEquals(2, quickStudy.tier(), "inline skills inherit their level as tier");
        assertEquals(2, quickStudy.cost());
        assertEquals(id("test:tutor"), quickStudy.profession());
        assertTrue(quickStudy.exclusiveWith().contains(id("test:slow_study")));
        assertTrue(tutor.skills().contains(id("test:tutor/quick_study")));
    }

    @Test
    void skillPointsAccumulatePerLevel() {
        ProfessionDef cook = loadCook(new LinkedHashMap<>());
        assertEquals(0, cook.skillPointsThrough(0));
        assertEquals(1, cook.skillPointsThrough(1));
        assertEquals(5, cook.skillPointsThrough(5), "one pick per level, five levels");
        assertEquals(5, cook.skillPointsThrough(30), "points stop at the last defined level");
    }

    @Test
    void levelTradesLandInMerchantMap() {
        // Scribe: no pheno requirements, so it parses without registered condition types. Its
        // progression ships as a levels.json sidecar, merged here the same way apply() does.
        JsonObject def = readResource("/data/townstead/profession/scribe/profession.json");
        ProfessionDataLoader.applyLevelsOverlay(def,
                readResource("/data/townstead/profession/scribe/levels.json"));
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id("townstead:scribe"));
        ProfessionDef scribe = ProfessionDataLoader.parseProfession(
                id("townstead:scribe"), def, Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(scribe);
        assertEquals(120, scribe.progression().dailyCap());
        assertEquals(2, scribe.trades().get(1).size());
        assertFalse(scribe.trades().containsKey(2));
    }

    @Test
    void levelsSidecarOverridesInline() {
        JsonObject def = JsonParser.parseString("""
                {"schema": "townstead:profession/v2",
                 "daily_cap": 10,
                 "levels": [{"xp": 5}, {}]}""").getAsJsonObject();
        JsonObject overlay = JsonParser.parseString("""
                {"daily_cap": 99,
                 "levels": [{"xp": 40}, {"xp": 60}, {}]}""").getAsJsonObject();
        ProfessionDataLoader.applyLevelsOverlay(def, overlay);
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id("test:tuned"));
        ProfessionDef tuned = ProfessionDataLoader.parseProfession(
                id("test:tuned"), def, Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(tuned);
        assertEquals(java.util.List.of(0, 40, 100), tuned.progression().tierThresholds());
        assertEquals(99, tuned.progression().dailyCap());
    }

    @Test
    void rankNumeralsCoverLongTracks() {
        assertEquals("II", ProfessionDef.roman(2));
        assertEquals("III", ProfessionDef.roman(3));
        assertEquals("IX", ProfessionDef.roman(9));
        assertEquals("XII", ProfessionDef.roman(12));
    }

    @Test
    void v1PointsFallBackToPointsPerTier() {
        JsonObject v1 = JsonParser.parseString("""
                {"schema": "townstead:profession/v1",
                 "points_per_tier": 2,
                 "progression": {"tiers": [0, 10, 20], "daily_cap": 5}}""").getAsJsonObject();
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id("test:legacy"));
        ProfessionDef legacy = ProfessionDataLoader.parseProfession(
                id("test:legacy"), v1, Map.of(), diagnostics);
        assertNotNull(legacy);
        assertTrue(legacy.levels().isEmpty());
        assertEquals(6, legacy.skillPointsThrough(3));
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static JsonObject readResource(String resource) {
        InputStream in = LevelSchemaTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        return JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static ProfessionDef load(String resource, String idRaw,
                                      Map<ResourceLocation, SkillDef> inlineOut) {
        JsonObject json = readResource(resource);
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id(idRaw));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id(idRaw), json, Map.of(), diagnostics, inlineOut);
        assertNotNull(def, resource + " must parse");
        return def;
    }
}
