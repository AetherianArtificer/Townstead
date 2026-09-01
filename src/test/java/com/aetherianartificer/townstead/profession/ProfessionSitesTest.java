package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
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

    @Test
    void functionalRawTypeKeepsItsSeatWhenPresentationTypeIsInherited() {
        JobSiteProvider.Building pizzerias = new JobSiteProvider.Building(
                List.of("compat/pizzadelight/pizzeria_l"), List.of(1, 2, 3),
                List.of("pizzaiolo"));

        assertEquals(1, ProfessionSites.seatsForBuilding(
                pizzerias, "compat/pizzadelight/pizzeria_l1", "house"));
    }

    @Test
    void specializedWorkerClaimsAnAffiliatedSeatBeforeGeneralWorkers() {
        JobSiteProvider.Building pizzerias = new JobSiteProvider.Building(
                List.of("compat/pizzadelight/pizzeria_l"), List.of(1, 2, 3),
                List.of("pizzaiolo"));
        List<JobSiteProvider> providers = List.of(KITCHENS, pizzerias);
        List<ProfessionSites.Site> sites = List.of(
                new ProfessionSites.Site(null, null, 0),
                new ProfessionSites.Site(null, null, 1));
        List<String> workers = Arrays.asList(null, "pizzaiolo");

        assertEquals(0, ProfessionSites.assignedSiteIndex(workers, sites, providers, 0));
        assertEquals(1, ProfessionSites.assignedSiteIndex(workers, sites, providers, 1));
    }

    @Test
    void pathAffinityIsAPreferenceRatherThanAWorksiteGate() {
        JobSiteProvider.Building pizzerias = new JobSiteProvider.Building(
                List.of("compat/pizzadelight/pizzeria_l"), List.of(1, 2, 3),
                List.of("pizzaiolo"));
        List<JobSiteProvider> providers = List.of(KITCHENS, pizzerias);
        List<ProfessionSites.Site> both = List.of(
                new ProfessionSites.Site(null, null, 0),
                new ProfessionSites.Site(null, null, 1));

        assertEquals(1, ProfessionSites.assignedSiteIndex(
                List.of("pizzaiolo"), both, providers, 0),
                "a Pizzaiolo prefers the Pizzeria even though Kitchen was declared first");
        assertEquals(0, ProfessionSites.assignedSiteIndex(
                List.of("pizzaiolo"), List.of(new ProfessionSites.Site(null, null, 0)),
                providers, 0),
                "a Pizzaiolo falls back to an ordinary Kitchen when no Pizzeria seat exists");
    }

    @Test
    void standaloneJobBlocksRespectSitesPerWorker() {
        List<BlockPos> hives = List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0),
                new BlockPos(4, 64, 0));

        assertEquals(List.of(hives.get(0), hives.get(4)),
                ProfessionCapacity.groupedAnchors(hives, 4),
                "four hives form one Beekeeper seat and the fifth starts another");
    }
}
