package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.client.gui.TemperatureIcons;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemperatureIconsTest {
    @Test
    void everyBodyTierFitsEachBackendAtlas() {
        for (String backend : new String[]{"cold_sweat", "legendary_survival_overhaul", "tough_as_nails", "builtin"}) {
            for (TemperatureData.Tier tier : TemperatureData.Tier.values()) {
                var icon = TemperatureIcons.icon(backend, tier);
                assertTrue(icon.u() >= 0 && icon.u() + icon.size() <= icon.width());
                assertTrue(icon.v() >= 0 && icon.v() + icon.size() <= icon.height());
                assertTrue(icon.overlayU() < 0 || icon.overlayU() + icon.size() <= icon.width());
            }
        }
    }

    @Test
    void coldSweatUsesOppositeEndsOfGaugeAndUnknownBackendFallsBack() {
        assertEquals(80, TemperatureIcons.icon("cold_sweat", TemperatureData.Tier.FREEZING).v());
        assertEquals(40, TemperatureIcons.icon("cold_sweat", TemperatureData.Tier.COMFORTABLE).v());
        assertEquals(0, TemperatureIcons.icon("cold_sweat", TemperatureData.Tier.SWELTERING).v());
        assertEquals(TemperatureIcons.icon("builtin", TemperatureData.Tier.COLD),
                TemperatureIcons.icon("unknown", TemperatureData.Tier.COLD));
    }
}
