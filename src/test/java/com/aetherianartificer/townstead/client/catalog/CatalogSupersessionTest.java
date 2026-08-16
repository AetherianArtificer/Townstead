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

    @Test
    void polymorphCandidatesHideSupersededFallbackAndPreserveOrder() {
        List<String> visible = CatalogDataLoader.withoutActiveSupersededBuildingTypes(
                List.of("bakery", "compat/bakery/bread_stand_l1", "house"),
                List.of("bakery", "compat/bakery/bread_stand_l1", "house"),
                List.of(BAKERIES));

        assertEquals(List.of("compat/bakery/bread_stand_l1", "house"), visible);
    }

    @Test
    void polymorphCandidatesKeepFallbackWhenProviderIsUnavailable() {
        List<String> visible = CatalogDataLoader.withoutActiveSupersededBuildingTypes(
                List.of("bakery", "house"),
                List.of("bakery", "house"),
                List.of(BAKERIES));

        assertEquals(List.of("bakery", "house"), visible);
    }

    @Test
    void recognitionRejectsSupersededFallbackEvenWhenItWasTheOnlyMatch() {
        List<String> recognized = CatalogDataLoader.withoutActiveSupersededBuildingTypesStrict(
                List.of("bakery"),
                List.of("bakery", "compat/bakery/bread_stand_l1"),
                List.of(BAKERIES));

        assertEquals(List.of(), recognized);
    }
}
