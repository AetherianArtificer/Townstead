package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfessionWorksitesTest {

    private static final JobSiteProvider.Building PIZZERIAS = new JobSiteProvider.Building(
            List.of("compat/pizzadelight/pizzeria_l"), List.of(1, 2, 3));

    @Test
    void professionBuildingCannotBecomeAnUnlimitedSecondaryWorksite() {
        assertTrue(ProfessionWorksites.isDeclaredBuildingSite(
                List.of(PIZZERIAS), "compat/pizzadelight/pizzeria_l1", "building"));
        assertTrue(ProfessionWorksites.isDeclaredBuildingSite(
                List.of(PIZZERIAS), "building", "compat/pizzadelight/pizzeria_l2"));
    }

    @Test
    void genuinelySecondaryBuildingStillUsesItsWorkerDeclaration() {
        assertFalse(ProfessionWorksites.isDeclaredBuildingSite(
                List.of(PIZZERIAS), "compat/rusticdelight/cafe_l1", "cafe"));
    }
}
