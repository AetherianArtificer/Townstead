package com.aetherianartificer.townstead.compat;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClimateDepotProvidersTest {
    @Test void sharedDepotsAreAvailableFromEitherProviderWithoutChangingOtherTanBuildings() {
        for (String path : List.of("compat/toughasnails/climate_fuel_store", "compat/toughasnails/climate_icehouse",
                "compat.toughasnails.climate_fuel_store", "compat.toughasnails.climate_icehouse"))
            assertEquals(List.of("toughasnails", "legendarysurvivaloverhaul"), ModCompat.providersForCompat(path));
        assertEquals(List.of("toughasnails"), ModCompat.providersForCompat("compat/toughasnails/another_building"));
    }
}
