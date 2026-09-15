package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Acceptance scenarios in explicit units, using LSO's native campfire/heater strengths. */
class ThermalGameplayModelTest {
    private final TemperatureSettings settings = TemperatureSettings.get();
    private double loss(double volume, double faces, ThermalConductance.Material material) {
        return faces * ThermalConductance.rate(material, settings.roomWallConductance(),
                settings.roomInsulatedConductance(), settings.roomOpeningConductance(), false, false, false)
                + RoomHeatBalance.ventilation(volume, settings.airChangesPerHour());
    }
    private double heat(double start, double outside, double volume, double conductance, double nativeStrength, double seconds) {
        return RoomHeatBalance.advance(start, volume * settings.roomHeatCapacity(),
                RoomHeatBalance.sourcePower(nativeStrength, settings.roomSourcePower(), 1, 1),
                conductance, outside, seconds * settings.thermalTimeScale());
    }

    @Test void campfireMakesSmallWoodenShelterUsefulWithinMinutes() {
        // A 5 x 5 x 3 volume has 110 boundary faces. LSO's campfire contributes +10.
        double conductance = loss(75, 110, ThermalConductance.Material.WOOD);
        double twoMinutes = heat(0, 0, 75, conductance, 10, 120);
        double fiveMinutes = heat(0, 0, 75, conductance, 10, 300);
        assertTrue(twoMinutes >= 18 && twoMinutes <= 21, "Small shelter should warm within two minutes");
        assertTrue(fiveMinutes >= 22 && fiveMinutes <= 25, "A modest fire should heat this reference shelter comfortably");
        assertEquals(0, heat(0, 0, 75, conductance, 0, 300));
    }

    @Test void sizeAndOpeningsMatterWithoutAnIndoorWarmthBonus() {
        double smallLoss = loss(75, 110, ThermalConductance.Material.WOOD);
        double largeLoss = loss(256, 256, ThermalConductance.Material.WOOD);
        double small = heat(0, 0, 75, smallLoss, 10, 300);
        assertTrue(heat(0, 0, 256, largeLoss, 10, 300) < small * 0.5);
        double openDoor = smallLoss + 2 * (settings.roomOpeningConductance() - settings.roomWallConductance() * 0.6);
        assertTrue(heat(0, 0, 75, openDoor, 10, 300) < small - 7);
    }

    @Test void insulationRetainsHeatRatherThanGeneratingIt() {
        double wood = loss(75, 110, ThermalConductance.Material.WOOD);
        double insulated = loss(75, 110, ThermalConductance.Material.INSULATION);
        assertTrue(heat(24, 0, 75, insulated, 0, 120) > heat(24, 0, 75, wood, 0, 120) + 10);
        assertEquals(0, heat(0, 0, 75, insulated, 0, 300));
        // An insulated occupied shelter is not hermetically sealed: ventilation still carries heat.
        assertTrue(insulated > 0);
    }

    @Test void nativeSourceRatiosCoolingAndSharedOutputSurviveCalibration() {
        double fire = RoomHeatBalance.sourcePower(10, settings.roomSourcePower(), 1, 1);
        double heater = RoomHeatBalance.sourcePower(15, settings.roomSourcePower(), 1, 1);
        assertEquals(fire * 1.5, heater);
        assertEquals(-heater, RoomHeatBalance.sourcePower(-15, settings.roomSourcePower(), 1, 1));
        assertEquals(heater, RoomHeatBalance.sourcePower(15, settings.roomSourcePower(), 1, 3)
                + RoomHeatBalance.sourcePower(15, settings.roomSourcePower(), 2, 3), 1e-8);
        double conductance = loss(75, 110, ThermalConductance.Material.WOOD);
        assertEquals(-heat(0, 0, 75, conductance, 15, 120), heat(0, 0, 75, conductance, -15, 120), 1e-8);
    }

    @Test void thermalClockChangesPacingButNotEquilibrium() {
        double conductance = loss(75, 110, ThermalConductance.Material.WOOD);
        double watts = RoomHeatBalance.sourcePower(10, settings.roomSourcePower(), 1, 1);
        assertEquals(watts / conductance, heat(0, 0, 75, conductance, 10, 10000), 1e-6);
        double equilibrium = watts / conductance;
        assertEquals(equilibrium, heat(equilibrium, 0, 75, conductance, 10, 1), 1e-8);
    }

    @Test void cookingProfilePreservesNativeStateStrengthAndDedicatedHeating() {
        double fraction = settings.cookingRoomHeatFraction();
        double stove = RoomHeatBalance.roomSourcePower(8, 250, 1, 1, true, fraction);
        assertEquals(300, stove, 1e-4);
        assertEquals(0, RoomHeatBalance.roomSourcePower(0, 250, 1, 1, true, fraction));
        assertEquals(stove / 2, RoomHeatBalance.roomSourcePower(4, 250, 1, 1, true, fraction));
        assertEquals(stove, RoomHeatBalance.roomSourcePower(8, 250, 1, 3, true, fraction)
                + RoomHeatBalance.roomSourcePower(8, 250, 2, 3, true, fraction), 1e-8);
        assertEquals(2500, RoomHeatBalance.roomSourcePower(10, 250, 1, 1, false, fraction));
        assertEquals(-3750, RoomHeatBalance.roomSourcePower(-15, 250, 1, 1, true, fraction));
    }
}
