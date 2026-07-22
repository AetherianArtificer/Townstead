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
    void cookLevelsCarryInlineSkillPool() {
        Map<ResourceLocation, SkillDef> inline = new LinkedHashMap<>();
        ProfessionDef cook = load("/data/townstead/profession/cook.json", "townstead:cook", inline);

        assertEquals(5, cook.levels().size());
        assertEquals(5, cook.progression().maxTier());
        assertEquals(java.util.List.of(0, 110, 300, 660, 1250), cook.progression().tierThresholds());
        assertEquals(230, cook.progression().dailyCap());

        assertEquals(5, inline.size(), "cook ships five inline skills");
        SkillDef openFlame = inline.get(id("townstead:open_flame"));
        assertNotNull(openFlame);
        assertEquals(3, openFlame.tier(), "inline skills inherit their level as tier");
        assertEquals(2, openFlame.cost());
        assertEquals(id("townstead:cook"), openFlame.profession());
        assertTrue(openFlame.exclusiveWith().contains(id("townstead:slow_stove")));
        assertTrue(cook.skills().contains(id("townstead:open_flame")),
                "inline skills join the flat membership list");
    }

    @Test
    void skillPointsAccumulatePerLevel() {
        ProfessionDef cook = load("/data/townstead/profession/cook.json", "townstead:cook",
                new LinkedHashMap<>());
        assertEquals(0, cook.skillPointsThrough(0));
        assertEquals(1, cook.skillPointsThrough(1));
        assertEquals(5, cook.skillPointsThrough(5));
        assertEquals(5, cook.skillPointsThrough(9), "points stop at the last defined level");
    }

    @Test
    void levelTradesLandInMerchantMap() {
        // Scribe: no pheno requirements, so it parses without registered condition types.
        ProfessionDef scribe = load("/data/townstead/profession/scribe.json", "townstead:scribe",
                new LinkedHashMap<>());
        assertEquals(2, scribe.trades().get(1).size());
        assertFalse(scribe.trades().containsKey(2));
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

    private static ProfessionDef load(String resource, String idRaw,
                                      Map<ResourceLocation, SkillDef> inlineOut) {
        InputStream in = LevelSchemaTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id(idRaw));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id(idRaw), json, Map.of(), diagnostics, inlineOut);
        assertNotNull(def, resource + " must parse");
        return def;
    }
}
