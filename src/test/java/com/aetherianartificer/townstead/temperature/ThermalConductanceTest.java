package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aetherianartificer.townstead.temperature.ThermalConductance.Material.*;

class ThermalConductanceTest {
    private static final double WALL = TemperatureSettings.get().roomWallConductance();
    private double rate(ThermalConductance.Material material) {
        return ThermalConductance.rate(material, WALL, 0.004, 1, false, false, false);
    }
    @Test void materialsHaveDistinctOrderedRetention() {
        assertTrue(rate(INSULATION) < rate(WOOD));
        assertTrue(rate(WOOD) < rate(EARTH));
        assertTrue(rate(EARTH) < rate(MASONRY));
        assertTrue(rate(MASONRY) < rate(GLASS));
        assertTrue(rate(GLASS) < rate(METAL));
        assertTrue(rate(METAL) < rate(POROUS));
    }
    @Test void openingDominatesMaterialAndClosedDoorStillLeaksAtSeals() {
        for (var material : ThermalConductance.Material.values())
            assertEquals(1, ThermalConductance.rate(material,WALL,0.004,1,false,true,true));
        assertTrue(ThermalConductance.rate(WOOD,WALL,0.004,1,false,true,false)>rate(WOOD));
    }
    @Test void thinWallsAndPanesTransferMoreHeat() {
        assertEquals(rate(WOOD)*2,ThermalConductance.rate(WOOD,WALL,0.004,1,true,false,false));
        assertEquals(rate(GLASS)*2,ThermalConductance.rate(GLASS,WALL,0.004,1,true,false,false));
    }
    @Test void twoCookingStovesWarmSmallKitchenWithoutRequiringSpecialVentilation() {
        var settings = TemperatureSettings.get();
        double conductance = 51*rate(WOOD)+12*rate(EARTH)+13*rate(MASONRY)
                +2*ThermalConductance.rate(WOOD,WALL,0.004,1,false,true,false)
                +RoomHeatBalance.ventilation(40, settings.airChangesPerHour());
        double stove = settings.appliance("minecraft:smoker").output(Float.NaN, true).watts();
        double power = 2 * stove;
        conductance += 2 * RoomHeatBalance.draft(stove, settings.fireMaxRise());
        double equilibrium = 24 + power / conductance;
        assertTrue(equilibrium >= 30 && equilibrium <= 24 + settings.fireMaxRise(),
                "Two lit stoves warm a small kitchen, bounded by the air their fires draw in: " + equilibrium);
        double recovering = RoomHeatBalance.advance(60, 40*settings.roomHeatCapacity(), power,
                conductance, 24, 120*settings.thermalTimeScale());
        assertTrue(recovering < 60 - 10 && recovering > equilibrium, "An overheated save cools toward the kitchen's balance: " + recovering);
        assertTrue(24 + power / (conductance + 4*settings.roomOpeningConductance()) < equilibrium);
        assertEquals(24, RoomHeatBalance.advance(43, 40*settings.roomHeatCapacity(),
                0, conductance, 24, 100000), 1e-6);
        assertTrue(RoomHeatBalance.advance(equilibrium, 40*settings.roomHeatCapacity(),
                power, conductance, 24, 60) >= equilibrium - 1e-8);
    }
    @Test void retainingHeatAlsoSlowsUnwantedSummerHeatGain() {
        assertTrue(RoomHeatBalance.advance(20,80,0,50*rate(INSULATION),35,60)
                <RoomHeatBalance.advance(20,80,0,50*rate(METAL),35,60));
    }
}
