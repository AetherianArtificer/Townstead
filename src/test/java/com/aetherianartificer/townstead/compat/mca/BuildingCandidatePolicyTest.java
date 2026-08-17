package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingCandidatePolicyTest {
    private static final CatalogDataLoader.GroupDef KITCHENS = new CatalogDataLoader.GroupDef(
            "townstead:kitchens", "Kitchens", "compat/farmersdelight/kitchen_l",
            "tiered", "compat/farmersdelight/kitchen_l", 10, List.of());
    private static final CatalogDataLoader.GroupDef CAFES = new CatalogDataLoader.GroupDef(
            "townstead:cafes", "Cafes", "compat/rusticdelight/cafe_l",
            "tiered", "compat/rusticdelight/cafe_l", 10, List.of());

    @Test
    void cumulativeKitchenMatchesCollapseToHighestTier() {
        assertEquals(List.of("compat/farmersdelight/kitchen_l5"),
                BuildingCandidatePolicy.collapseTierFamilies(List.of(
                        "compat/farmersdelight/kitchen_l3",
                        "compat/farmersdelight/kitchen_l5",
                        "compat/farmersdelight/kitchen_l1"), List.of(KITCHENS)));
    }

    @Test
    void differentFamiliesRemainAmbiguous() {
        assertEquals(List.of(
                        "compat/farmersdelight/kitchen_l4",
                        "compat/rusticdelight/cafe_l3"),
                BuildingCandidatePolicy.collapseTierFamilies(List.of(
                        "compat/farmersdelight/kitchen_l4",
                        "compat/rusticdelight/cafe_l3"), List.of(KITCHENS, CAFES)));
    }

    @Test
    void malformedTierFamilyRemainsAmbiguous() {
        CatalogDataLoader.GroupDef mixed = new CatalogDataLoader.GroupDef(
                "townstead:mixed", "Mixed", "compat/mixed/", "tiered",
                "compat/mixed/", 10, List.of());
        assertEquals(List.of("compat/mixed/basic", "compat/mixed/basic_l2"),
                BuildingCandidatePolicy.collapseTierFamilies(
                        List.of("compat/mixed/basic", "compat/mixed/basic_l2"), List.of(mixed)));
    }

    @Test
    void untieredGroupsAreNeverCollapsed() {
        CatalogDataLoader.GroupDef facilities = new CatalogDataLoader.GroupDef(
                "townstead:facilities", "Facilities", "compat/butchery/", "grid",
                "compat/butchery/", 10, List.of());
        assertEquals(List.of("compat/butchery/tannery", "compat/butchery/smokehouse"),
                BuildingCandidatePolicy.collapseTierFamilies(
                        List.of("compat/butchery/tannery", "compat/butchery/smokehouse"),
                        List.of(facilities)));
    }
}
