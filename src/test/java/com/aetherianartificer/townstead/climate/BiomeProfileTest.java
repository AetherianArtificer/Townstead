package com.aetherianartificer.townstead.climate;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BiomeProfileTest {
    private static BiomeProfile shipped(String biome) {
        var stream = BiomeProfileTest.class.getResourceAsStream("/data/minecraft/climate/biome/" + biome + ".json");
        assertNotNull(stream, biome);
        JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        return BiomeProfile.parse(json);
    }

    @Test
    void swingIsHalfTheGapBetweenTheMonths() {
        var badlands = shipped("badlands");
        assertEquals(13.9f, badlands.mean(), 1e-4);
        assertEquals(14.2f, badlands.swing(), 1e-4);
        assertEquals(75, badlands.typicalY());
    }

    @Test
    void profileKeepsItsZonesIdAndWetDryOffsets() {
        var zone = new ClimateZone(ResourceLocation.tryParse("townstead:semi_arid"), ClimateZone.SeasonKind.WET_DRY,
                5f, 1f / 12f, 11f, 0.3f, 0f, 3f, -1f, 0.4f);
        var savanna = shipped("savanna").asZone(ResourceLocation.tryParse("townstead:semi_arid"), zone);
        assertEquals(ResourceLocation.tryParse("townstead:semi_arid"), savanna.id());
        assertEquals(6f, savanna.diurnalRange(), 1e-4);
        assertEquals(3f, savanna.dryOffset());
        assertEquals(0.4f, savanna.noSeasonFraction());
        assertEquals(0f, savanna.meanOffset());
    }

    @Test
    void desertNightsStayCoolWithoutSeasons() {
        assertEquals(0f, shipped("desert").noSeasonFraction());
    }

    @Test
    void netherAndEndBiomesHaveNoSeasons() {
        for (String biome : new String[]{"nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley",
                "basalt_deltas", "the_end", "end_highlands", "end_midlands", "small_end_islands", "end_barrens"}) {
            var profile = shipped(biome);
            assertEquals(ClimateZone.SeasonKind.NONE, profile.seasonKind(), biome);
            assertEquals(0f, profile.asZone(ResourceLocation.tryParse("minecraft:" + biome), null).seasonalSwing(), biome);
        }
        assertEquals(55f, shipped("basalt_deltas").mean());
        assertEquals(30f, shipped("soul_sand_valley").mean());
    }

    @Test
    void caveBiomesShiftTheGroundAndTheDeepDarkIsChilled() {
        assertEquals(2f, shipped("lush_caves").undergroundOffset());
        assertEquals(-1f, shipped("dripstone_caves").undergroundOffset());
        assertTrue(shipped("deep_dark").undergroundOffset() <= -20f, "sculk chill outweighs the deep-rock heat");
        assertEquals(0f, shipped("plains").undergroundOffset());
    }

    @Test
    void altitudeCoolsAboveTheUsualHeightAndWarmsBelowItDownToSeaLevel() {
        assertEquals(-2f, ClimateZones.altitudeOffset(130, 100, 63, true, 15f), 1e-5);
        assertEquals(0f, ClimateZones.altitudeOffset(100, 100, 63, true, 15f), 1e-5);
        assertEquals(2f, ClimateZones.altitudeOffset(70, 100, 63, true, 15f), 1e-5);
        assertEquals(37f / 15f, ClimateZones.altitudeOffset(20, 100, 63, true, 15f), 1e-5);
        assertEquals(0f, ClimateZones.altitudeOffset(40, 63, 63, false, 15f), 1e-5);
    }
}
