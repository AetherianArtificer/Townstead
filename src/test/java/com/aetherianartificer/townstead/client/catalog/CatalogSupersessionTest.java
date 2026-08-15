package com.aetherianartificer.townstead.client.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CatalogSupersessionTest {
    private static final CatalogDataLoader.GroupDef BAKERIES = new CatalogDataLoader.GroupDef(
            "townstead:bakery_bakeries",
            "Bakeries",
            "compat/bakery/",
            "tiered",
            "compat/bakery/",
            10,
            List.of("bakery"));

    @Test
    void activeProviderGroupSupersedesFallback() {
        Set<String> hidden = CatalogDataLoader.activeSupersededBuildingTypes(
                List.of("bakery", "compat/bakery/bread_stand_l1"),
                List.of(BAKERIES));

        assertEquals(Set.of("bakery"), hidden);
    }

    @Test
    void absentProviderGroupPreservesFallback() {
        Set<String> hidden = CatalogDataLoader.activeSupersededBuildingTypes(
                List.of("bakery", "house"),
                List.of(BAKERIES));

        assertEquals(Set.of(), hidden);
    }
}
