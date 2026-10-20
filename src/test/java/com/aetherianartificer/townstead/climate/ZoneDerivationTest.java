package com.aetherianartificer.townstead.climate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ZoneDerivationTest {
    private static final List<String> ZONES = List.of("polar", "subarctic", "continental", "temperate", "maritime",
            "mediterranean", "arid", "semi_arid", "tropical_humid", "alpine", "subterranean");

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw.contains(":") ? raw : "townstead:" + raw);
    }

    private static Set<ResourceLocation> zoneIds() {
        Set<ResourceLocation> ids = new HashSet<>();
        for (String zone : ZONES) ids.add(id(zone));
        return ids;
    }

    private static JsonObject resource(String path) {
        var stream = ZoneDerivationTest.class.getResourceAsStream("/data/townstead/" + path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static ZoneDerivation shipped() {
        return ZoneDerivation.parse(resource("climate/derivation.json"));
    }

    private static ZoneDerivation.BiomeFacts biome(String name, float temperature, float downfall, String... tags) {
        Set<ResourceLocation> set = new HashSet<>();
        for (String tag : tags) set.add(id(tag));
        return new ZoneDerivation.BiomeFacts(id(name), set, temperature, downfall);
    }

    @Test
    void everyShippedZoneParsesAndEveryRuleNamesOne() {
        for (String zone : ZONES) assertNotNull(ClimateZone.parse(id(zone), resource("climate/zone/" + zone + ".json")));
        for (var rule : shipped().rules())
            assertTrue(rule.zone() == null || zoneIds().contains(rule.zone()), "unknown zone " + rule.zone());
        assertEquals(id("temperate"), shipped().fallback());
    }

    @Test
    void zoneTagWinsOverEveryRule() {
        var r = shipped().resolve(biome("examplepack:cold_coast", 0.0f, 0.9f,
                "townstead:climate/maritime", "c:is_snowy"), zoneIds());
        assertEquals(id("maritime"), r.zone());
        assertEquals("tag", r.step());
    }

    @Test
    void seasonModTagsComeBeforeConventionalTags() {
        var r = shipped().resolve(biome("examplepack:monsoon_hills", 0.8f, 0.9f,
                "eclipticseasons:rain/monsoonal", "c:is_mountain/slope"), zoneIds());
        assertEquals(id("tropical_humid"), r.zone());
        assertEquals("provider", r.step());
    }

    @Test
    void sereneTropicalSplitsByDownfall() {
        assertEquals(id("tropical_humid"), shipped().resolve(biome("examplepack:wetland", 0.95f, 0.9f,
                "sereneseasons:tropical_biomes"), zoneIds()).zone());
        assertEquals(id("arid"), shipped().resolve(biome("examplepack:dunes", 2.0f, 0.0f,
                "sereneseasons:tropical_biomes"), zoneIds()).zone());
        assertEquals(id("semi_arid"), shipped().resolve(biome("examplepack:scrub", 1.2f, 0.4f,
                "sereneseasons:tropical_biomes"), zoneIds()).zone());
    }

    @Test
    void conventionalTagsPlaceModdedBiomes() {
        assertEquals(id("subterranean"), shipped().resolve(biome("examplepack:crystal_cave", 0.8f, 0.4f, "c:is_cave"), zoneIds()).zone());
        assertEquals(id("arid"), shipped().resolve(biome("examplepack:salt_flats", 1.5f, 0.0f,
                "c:is_hot/overworld", "c:is_dry/overworld"), zoneIds()).zone());
        assertEquals(id("tropical_humid"), shipped().resolve(biome("examplepack:cloud_forest", 0.9f, 0.9f,
                "forge:is_hot/overworld", "forge:is_wet/overworld"), zoneIds()).zone());
    }

    @Test
    void netherAndEndBiomesStayUnzoned() {
        var r = shipped().resolve(biome("examplepack:ash_plain", 2.0f, 0.0f, "minecraft:is_nether"), zoneIds());
        assertNull(r.zone());
        assertEquals("conventional", r.step());
    }

    @Test
    void mediterraneanBiomesJoinByName() {
        var r = shipped().resolve(biome("examplepack:mediterranean_coast", 0.8f, 0.4f), zoneIds());
        assertEquals(id("mediterranean"), r.zone());
        assertEquals("keyword", r.step());
    }

    @Test
    void untaggedBiomesFallBackToTheirClimate() {
        assertEquals(id("polar"), shipped().resolve(biome("examplepack:frost", 0.05f, 0.5f), zoneIds()).zone());
        assertEquals(id("subarctic"), shipped().resolve(biome("examplepack:pines", 0.3f, 0.5f), zoneIds()).zone());
        assertEquals(id("arid"), shipped().resolve(biome("examplepack:waste", 1.5f, 0.1f), zoneIds()).zone());
        assertEquals(id("maritime"), shipped().resolve(biome("examplepack:marsh", 0.7f, 0.9f), zoneIds()).zone());
        var plain = shipped().resolve(biome("examplepack:fields", 0.7f, 0.4f), zoneIds());
        assertEquals(id("temperate"), plain.zone());
        assertEquals("fallback", plain.step());
    }

    @Test
    void rulesNamingAnUnloadedZoneAreSkipped() {
        Set<ResourceLocation> withoutPolar = zoneIds();
        withoutPolar.remove(id("polar"));
        assertEquals(id("subarctic"), shipped().resolve(biome("examplepack:frost", 0.05f, 0.5f), withoutPolar).zone());
    }

    @Test
    void packRulesRunFirstAndReplaceDropsTheDefaults() {
        JsonObject pack = JsonParser.parseString("""
                { "rules": [ { "step": "climate", "max_temperature": 0.8, "zone": "townstead:continental" } ] }
                """).getAsJsonObject();
        var layered = shipped().layeredUnder(ZoneDerivation.parse(pack), false);
        assertEquals(id("continental"), layered.resolve(biome("examplepack:frost", 0.05f, 0.5f), zoneIds()).zone());
        assertEquals(id("arid"), layered.resolve(biome("examplepack:waste", 1.5f, 0.1f), zoneIds()).zone());
        var replaced = shipped().layeredUnder(ZoneDerivation.parse(pack), true);
        assertNull(replaced.fallback());
        assertNull(replaced.resolve(biome("examplepack:waste", 1.5f, 0.1f), zoneIds()).zone());
    }
}
