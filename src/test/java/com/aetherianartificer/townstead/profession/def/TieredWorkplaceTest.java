package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tiered workplaces, read off the career def's building entry. This used to be a cook-owned
 * switch statement and a hardcoded kitchen prefix; both the prefix and the ladder are data now,
 * so the rules are pinned where any trade reads them.
 */
class TieredWorkplaceTest {

    private static JobSiteProvider.Building kitchens() {
        return new JobSiteProvider.Building(
                List.of("compat/farmersdelight/kitchen_l"), List.of(1, 1, 2, 2, 3));
    }

    @Test
    void buildingTypePrefixDetection() {
        JobSiteProvider.Building kitchens = kitchens();
        assertTrue(kitchens.matches("compat/farmersdelight/kitchen_l1"));
        assertTrue(kitchens.matches("compat/farmersdelight/kitchen_l5"));
        assertFalse(kitchens.matches("compat/othermod/kitchen_l1"));
        assertFalse(kitchens.matches(null));
        // The bare prefix still matches — it is the same family, just untiered.
        assertTrue(kitchens.matches("compat/farmersdelight/kitchen_l"));
    }

    @Test
    void namespacedPackPrefixMatchesMcaFlattenedBuildingId() {
        JobSiteProvider.Building apiary = new JobSiteProvider.Building(
                List.of("townstead_beekeeping:apiary"));

        assertTrue(apiary.matches("townstead_beekeeping/apiary"));
        assertEquals(1, apiary.slotsFor("townstead_beekeeping/apiary"));
        assertFalse(apiary.matches("other_pack/apiary"));
    }

    @Test
    void tierParsingCoversHappyAndErrorPaths() {
        JobSiteProvider.Building kitchens = kitchens();
        assertEquals(1, kitchens.tierOf("compat/farmersdelight/kitchen_l1"));
        assertEquals(3, kitchens.tierOf("compat/farmersdelight/kitchen_l3"));
        assertEquals(5, kitchens.tierOf("compat/farmersdelight/kitchen_l5"));

        assertEquals(0, kitchens.tierOf("compat/farmersdelight/kitchen_lx"));
        assertEquals(0, kitchens.tierOf("compat/farmersdelight/kitchen_l"));
        assertEquals(0, kitchens.tierOf("other:path"));
        assertEquals(0, kitchens.tierOf(null));
    }

    @Test
    void slotScalingComesFromTheDeclaredLadder() {
        JobSiteProvider.Building kitchens = kitchens();
        assertEquals(1, kitchens.slotsFor("compat/farmersdelight/kitchen_l1"));
        assertEquals(1, kitchens.slotsFor("compat/farmersdelight/kitchen_l2"));
        assertEquals(2, kitchens.slotsFor("compat/farmersdelight/kitchen_l3"));
        assertEquals(2, kitchens.slotsFor("compat/farmersdelight/kitchen_l4"));
        assertEquals(3, kitchens.slotsFor("compat/farmersdelight/kitchen_l5"));

        assertEquals(0, kitchens.slotsFor("compat/othermod/kitchen_l1"), "not ours, seats nobody");
        assertEquals(0, kitchens.slotsFor(null));
        // A building we match but cannot read a tier from is still a workplace: retiring a whole
        // building over a naming slip is worse than seating one worker in it.
        assertEquals(1, kitchens.slotsFor("compat/farmersdelight/kitchen_lx"));
        assertEquals(1, kitchens.slotsFor("compat/farmersdelight/kitchen_l9"));
    }

    @Test
    void anUntieredBuildingSeatsOneWorker() {
        JobSiteProvider.Building plain = new JobSiteProvider.Building(List.of("bakery"));
        assertEquals(1, plain.slotsFor("bakery"));
        assertEquals(0, plain.tierOf("bakery"));
        assertEquals(0, plain.slotsFor("library"));
    }

    @Test
    void proprietorReservationParsesAndAppliesToTheFirstSeatAtEveryTier() {
        JobSiteProvider.Building bars = (JobSiteProvider.Building) JobSiteProviders.parse(
                JsonParser.parseString("""
                        {"type":"townstead:building",
                         "type_prefix":"compat/beachparty/beach_cocktail_bar_l",
                         "slots_per_tier":[1,2,3],
                         "proprietor":{"professions":["beachparty:sandymerchant"],"slots":1}}
                        """).getAsJsonObject());

        assertEquals(Set.of(ResourceLocation.tryParse("beachparty:sandymerchant")),
                bars.requiredProfessionsForSeat(
                        "compat/beachparty/beach_cocktail_bar_l3", 0));
        assertTrue(bars.requiredProfessionsForSeat(
                        "compat/beachparty/beach_cocktail_bar_l3", 1).isEmpty());
        assertTrue(bars.requiredProfessionsForSeat(
                        "compat/beachparty/beach_cocktail_bar_l3", 2).isEmpty());
    }
}
