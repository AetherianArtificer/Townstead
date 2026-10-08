package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ThermalStorageTest {
    @Test void sealedExchangeConservesEnergyAndWarmWallsHeatColdAir() {
        var wall = new ThermalStorage.Surface(12000, 25, 0, 0, 30);
        var result = ThermalStorage.advance(10, 2000, 0, 0, 0, List.of(wall), 120);
        assertTrue(result.air() > 10 && result.air() < 30);
        assertTrue(result.surfaces()[0] < 30);
        assertEquals(10*2000 + 30*12000, result.air()*2000 + result.surfaces()[0]*12000, 1e-6);
    }

    @Test void sourceEnergyIsStoredRatherThanLostOrDuplicated() {
        var wall = new ThermalStorage.Surface(12000, 25, 0, 0, 10);
        var result = ThermalStorage.advance(10, 2000, 300, 0, 0, List.of(wall), 120);
        assertEquals(10*14000 + 300*120, result.air()*2000 + result.surfaces()[0]*12000, 1e-6);
    }

    @Test void energyLeavingSurfacesAndVentilationMatchesTotalEnergyChange() {
        var wall = new ThermalStorage.Surface(12000, 25, 2, 0, 20);
        var result = ThermalStorage.advance(30, 2000, 300, 3, 0, List.of(wall), 5);
        double energyChange = (result.air()-30)*2000 + (result.surfaces()[0]-20)*12000;
        assertEquals(5*(300-3*result.air()-2*result.surfaces()[0]), energyChange, 1e-6);
    }

    @Test void woodenShelterStillWarmsWithColdWallsAndStoresHeatAfterFireStops() {
        var s = TemperatureSettings.get();
        var surfaces = new java.util.ArrayList<ThermalStorage.Surface>();
        for (int i = 0; i < 110; i++)
            surfaces.add(ThermalStorage.surface(s.materialHeatCapacity(ThermalConductance.Material.WOOD)/6, .84, 0, 0));
        double ventilation = RoomHeatBalance.ventilation(75, s.airChangesPerHour());
        var warm = ThermalStorage.advance(0, 75*s.roomHeatCapacity(), 2500, ventilation, 0, surfaces, 300*s.thermalTimeScale());
        assertTrue(warm.air() >= 20 && warm.air() <= 24, "Cold walls must not make the reference shelter unusable: " + warm.air());
        var warmedSurfaces = new java.util.ArrayList<ThermalStorage.Surface>();
        for (int i = 0; i < surfaces.size(); i++) {
            var p = surfaces.get(i);
            warmedSurfaces.add(new ThermalStorage.Surface(p.capacity(), p.airConductance(), p.outerConductance(), 0, warm.surfaces()[i]));
        }
        var retained = ThermalStorage.advance(warm.air(), 75*s.roomHeatCapacity(), 0, ventilation, 0, warmedSurfaces, 60*s.thermalTimeScale());
        var coldWalls = ThermalStorage.advance(warm.air(), 75*s.roomHeatCapacity(), 0, ventilation, 0, surfaces, 60*s.thermalTimeScale());
        assertTrue(retained.air() > coldWalls.air());
    }

    @Test void coldMassAbsorbsHeatAndMoreCapacitySlowsWarmingAtSameInsulation() {
        var light = ThermalStorage.surface(4000, 1, 0, 0);
        var heavy = ThermalStorage.surface(16000, 1, 0, 0);
        var a = ThermalStorage.advance(20, 2000, 100, 0, 0, List.of(light), 300);
        var b = ThermalStorage.advance(20, 2000, 100, 0, 0, List.of(heavy), 300);
        assertTrue(b.air() < a.air());
        assertTrue(b.surfaces()[0] > 0);
        assertTrue(b.surfaces()[0] < a.surfaces()[0]);
    }

    @Test void storageDoesNotChangeSteadyHeatLossOrCreatePermanentHeat() {
        var wall = ThermalStorage.surface(12000, 2, 0, 0);
        var result = ThermalStorage.advance(0, 2000, 100, 3, 0, List.of(wall), 1000000);
        assertEquals(20, result.air(), 1e-4); // 100 W / (2 + 3) W/K
        assertEquals(2, 1 / (1 / wall.airConductance() + 1 / wall.outerConductance()), 1e-12);
        var coolingWall = new ThermalStorage.Surface(12000, wall.airConductance(), wall.outerConductance(), 0, result.surfaces()[0]);
        var cold = ThermalStorage.advance(result.air(), 2000, 0, 3, 0, List.of(coolingWall), 1000000);
        assertEquals(0, cold.air(), 1e-4);
        assertEquals(0, cold.surfaces()[0], 1e-4);
    }

    @Test void extremeConductanceAndLongStepDoNotOvershootOrProduceNaN() {
        var result = ThermalStorage.advance(-20, 1, 0, 0, 0,
                List.of(new ThermalStorage.Surface(1, 1e9, 1e9, 40, 0)), 10000);
        assertTrue(Double.isFinite(result.air()));
        assertTrue(result.air() >= -20 && result.air() <= 40.000001);
        assertTrue(result.surfaces()[0] >= 0 && result.surfaces()[0] <= 40.000001);
    }

    @Test void materialStorageIsIndependentOfInsulationAndPackTunable() {
        var settings = TemperatureSettings.parse(com.google.gson.JsonParser.parseString(
                "{\"thermal_material_capacity_j_per_block_k\":{\"wood\":9000,\"metal\":-1}}").getAsJsonObject());
        assertEquals(9000, settings.materialHeatCapacity(ThermalConductance.Material.WOOD));
        assertEquals(16000, settings.materialHeatCapacity(ThermalConductance.Material.METAL));
        assertTrue(settings.materialHeatCapacity(ThermalConductance.Material.MASONRY)
                > settings.materialHeatCapacity(ThermalConductance.Material.INSULATION));
    }

    @Test void kitchenStaysUsableAndSavedOverheatingDissipatesWithWarmWalls() {
        var s = TemperatureSettings.get();
        double capacity = 40 * s.roomHeatCapacity();
        double g = 51*.84 + 12*1.12 + 13*1.4 + 2*1.4;
        double wallMass = (51*4000 + 12*16000 + 13*12000 + 2*4000*.125)/6.0;
        double power = 2 * RoomHeatBalance.roomSourcePower(8.25, s.roomSourcePower(), 1, 1, true, s.cookingRoomHeatFraction());
        double ventilation = RoomHeatBalance.ventilation(40, s.airChangesPerHour());
        var result = ThermalStorage.advance(60, capacity, power, ventilation, 24,
                List.of(ThermalStorage.surface(wallMass, g, 24, 60)), 300*s.thermalTimeScale());
        assertTrue(result.air() < 35, "Even stored wall heat should dissipate within five minutes: " + result.air());
        var steady = ThermalStorage.advance(24, capacity, power, ventilation, 24,
                List.of(ThermalStorage.surface(wallMass, g, 24, 24)), 1000000);
        assertTrue(steady.air() > 28 && steady.air() < 33);
    }
}
