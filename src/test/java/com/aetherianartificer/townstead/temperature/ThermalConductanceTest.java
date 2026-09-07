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
    @Test void savedKitchenTwoStoveBalanceIsUsableWithoutSpecialVentilation() {
        // Exposed faces from MoreTest's small kitchen, excluding faces that face back into the same region.
        double conductance=51*rate(WOOD)+12*rate(EARTH)+13*rate(MASONRY)
                +2*ThermalConductance.rate(WOOD,WALL,0.004,1,false,true,false);
        for (double outside : new double[]{-1, 6, 24}) {
            double equilibrium=RoomHeatBalance.advance(outside,80,2*8.25*1.5,conductance,outside,10000);
            double rise=equilibrium-outside;
            assertTrue(rise>=5 && rise<=8, "Kitchen rise above " + outside + " C: " + rise);
            assertTrue(RoomHeatBalance.advance(outside,80,3*8.25*1.5,conductance,outside,10000)>equilibrium);
        }
        double cooling=RoomHeatBalance.advance(43,80,2*8.25*1.5,conductance,24,180);
        assertTrue(cooling<32, "Previously overheated kitchen should recover gradually even in summer");
    }
    @Test void retainingHeatAlsoSlowsUnwantedSummerHeatGain() {
        assertTrue(RoomHeatBalance.advance(20,80,0,50*rate(INSULATION),35,60)
                <RoomHeatBalance.advance(20,80,0,50*rate(METAL),35,60));
    }
}
