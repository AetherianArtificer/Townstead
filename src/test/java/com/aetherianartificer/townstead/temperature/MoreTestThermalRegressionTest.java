package com.aetherianartificer.townstead.temperature;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MoreTestThermalRegressionTest {
    @Test void authoredCampfireRecoversColdVillagerEvenBesideSnow() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/townstead/need/temperature.json")) {
            assertNotNull(stream);
            var settings = TemperatureSettings.parse(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            var fire = settings.appliance("minecraft:campfire").output(10, true);
            double snow = RoomHeatBalance.localExposure(settings.coolingSourceOffset(), 1);
            // Freezing outdoor climate, adjacent lit fire, a large surrounding snow field.
            float felt = (float) (RoomHeatBalance.localExposure(fire.radiantDegrees(), 1)
                    - RoomHeatBalance.combinedExposure(-snow * 100, -snow));
            var exposure = new ThermalExposure(felt, 0, false, false, 0,
                    ThermalProtection.NONE, ThermalProfile.DEFAULT, false);
            var forecast = exposure.forecast(36.3f, 300); // Jorvain's saved core temperature
            assertTrue(forecast.recovers(), "A nearby fire must actually finish a recovery break");
            assertTrue(forecast.body() > 36.75f);
            assertTrue(forecast.body() < 37.5f);
            assertEquals(2500, fire.watts(), "Radiant relief must not increase kitchen/room heat input");
            assertEquals(0, settings.appliance("minecraft:campfire").output(0, false).radiantDegrees());
        }
    }

    @Test void manyCoolingBlocksCannotFreezeTheSurroundingStoneWithoutLimit() {
        var sink = RoomHeatBalance.cooling(-2000 * 53, 20, 8);
        double air = 20, stone = 20;
        for (int i = 0; i < 100; i++) {
            var result = ThermalNetwork.advance(List.of(
                    new ThermalNetwork.Node(53 * 2000, air, 0, sink.conductance(), sink.reservoir()),
                    new ThermalNetwork.Node(100 * 72000, stone, 0, 0, 20)),
                    List.of(new ThermalNetwork.Link(0, 1, 100)), 500);
            air = result.temperatures()[0]; stone = result.temperatures()[1];
            assertTrue(air >= sink.reservoir() - 1e-8 && stone >= sink.reservoir() - 1e-8);
            assertEquals(0, result.errorJoules(), .001);
        }
        assertTrue(air < 20 && stone < 20, "Cooling still works");
    }

    @Test void poweredCoolerHasFiniteEquilibriumInAnInsulatedRoom() {
        var cooling = RoomHeatBalance.cooling(-3750, 20, 25);
        var result = ThermalNetwork.advance(List.of(new ThermalNetwork.Node(2000, 20, 0,
                cooling.conductance(), cooling.reservoir())), List.of(), 10000);
        assertEquals(-5, result.temperatures()[0], 1e-8);
    }

    @Test void waterIsNotAnUnlimitedSourceOfColdAir() throws Exception {
        // processResources renames the tag directory per version: "block" on 1.21.1,
        // "blocks" on 1.20.1. Look for both so this asserts on the real shipped tag
        // instead of passing vacuously, or failing, on whichever line it is not.
        var found = getClass().getResourceAsStream("/data/townstead/tags/block/thermal/cooling_sources.json");
        if (found == null) {
            found = getClass().getResourceAsStream("/data/townstead/tags/blocks/thermal/cooling_sources.json");
        }
        try (var stream = found) {
            assertNotNull(stream);
            var values = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("values");
            for (var value : values) assertNotEquals("minecraft:water", value.getAsString());
        }
    }
}
