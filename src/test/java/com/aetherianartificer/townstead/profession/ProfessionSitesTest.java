package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionSitesTest {

    private static final JobSiteProvider.Building KITCHENS = new JobSiteProvider.Building(
            List.of("compat/farmersdelight/kitchen_l"), List.of(1, 1, 2, 2, 3));

    @Test
    void inheritedEffectiveKitchenTypeOwnsTheSeatAndTier() {
        assertEquals(2, ProfessionSites.seatsForBuilding(
                KITCHENS, "room", "compat/farmersdelight/kitchen_l3"));
    }

    @Test
    void rawTypeRemainsTheFallbackWithoutAnEffectiveType() {
        assertEquals(1, ProfessionSites.seatsForBuilding(
                KITCHENS, "compat/farmersdelight/kitchen_l2", null));
    }
}
