package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
    void nativeProprietorClaimsTheReservedSeatBeforeOrdinaryCareerStaff() {
        ResourceLocation sandyMerchant = id("beachparty:sandymerchant");
        JobSiteProvider.Building bars = new JobSiteProvider.Building(
                List.of("compat/beachparty/beach_cocktail_bar_l"), List.of(1, 2, 3),
                List.of("bartender"), new JobSiteProvider.Building.Proprietor(
                        java.util.Set.of(sandyMerchant), 1));
        List<JobSiteProvider> providers = List.of(bars);
        List<ProfessionSites.Site> sites = List.of(
                new ProfessionSites.Site(null, null, 0,
                        bars.requiredProfessionsForSeat(
                                "compat/beachparty/beach_cocktail_bar_l2", 0)),
                new ProfessionSites.Site(null, null, 0,
                        bars.requiredProfessionsForSeat(
                                "compat/beachparty/beach_cocktail_bar_l2", 1)));
        List<String> paths = List.of("bartender", "bartender");
        List<ResourceLocation> professions = List.of(
                id("townstead:beverage_artisan"), sandyMerchant);

        assertEquals(1, ProfessionSites.assignedSiteIndex(
                paths, professions, sites, providers, 0));
        assertEquals(0, ProfessionSites.assignedSiteIndex(
                paths, professions, sites, providers, 1));
    }

    @Test
    void ordinaryCareerStaffCannotEraseAnUnfilledProprietorPosition() {
        ResourceLocation sandyMerchant = id("beachparty:sandymerchant");
        JobSiteProvider.Building bars = new JobSiteProvider.Building(
                List.of("compat/beachparty/beach_cocktail_bar_l"), List.of(1, 2, 3),
                List.of("bartender"), new JobSiteProvider.Building.Proprietor(
                        java.util.Set.of(sandyMerchant), 1));
        ProfessionSites.Site reserved = new ProfessionSites.Site(null, null, 0,
                bars.requiredProfessionsForSeat(
                        "compat/beachparty/beach_cocktail_bar_l1", 0));

        assertEquals(-1, ProfessionSites.assignedSiteIndex(
                List.of("bartender"), List.of(id("townstead:beverage_artisan")),
                List.of(reserved), List.of(bars), 0));
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

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
