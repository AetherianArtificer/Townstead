package com.aetherianartificer.townstead.work.site;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A worksite's name is the only part of it a player ever types or reads, so the rules around it are
 * the difference between "The Kitchen" in a message and {@code kitchen_l3}.
 */
class WorksiteNamesTest {

    @Test
    void buildingTypesBecomeSomethingAPlayerWouldSay() {
        assertEquals("Kitchen", WorksiteNames.fromBuildingType("kitchen_l3"));
        assertEquals("Kitchen", WorksiteNames.fromBuildingType("townstead:kitchen_l1"));
        assertEquals("Cafe", WorksiteNames.fromBuildingType("cafe_l5"));
        assertEquals("Butcher Shop", WorksiteNames.fromBuildingType("butcher_shop"));
        assertEquals("", WorksiteNames.fromBuildingType(null));
    }

    @Test
    void tierSuffixesGoButRealNamesEndingInLettersStay() {
        assertEquals("Hall", WorksiteNames.fromBuildingType("hall"),
                "only a trailing lN marker is a tier");
        assertEquals("Level Crossing", WorksiteNames.fromBuildingType("level_crossing"),
                "a word starting with l is not a tier suffix");
    }

    @Test
    void displayNeverComesBackBlank() {
        Worksite unnamed = new Worksite(12L, key(), "", 1, 0L, 0L);
        assertEquals("Worksite 12", WorksiteNames.display(unnamed),
                "an ugly name beats a nameless place in a message");
        assertEquals("Unknown worksite", WorksiteNames.display(null));

        Worksite named = new Worksite(12L, key(), "The Kitchen", 1, 0L, 0L);
        assertEquals("The Kitchen", WorksiteNames.display(named));
    }

    @Test
    void playerNamesAreCleanedNotTrusted() {
        assertEquals("The Kitchen", WorksiteNames.sanitise("  The   Kitchen  "),
                "runs of whitespace collapse so two names cannot look identical but differ");
        assertEquals("Kitchen", WorksiteNames.sanitise("§cKitchen"),
                "formatting codes would let a name recolour the rest of a chat line");
        assertNull(WorksiteNames.sanitise("   "));
        assertNull(WorksiteNames.sanitise(null));
    }

    @Test
    void namesAreLengthCapped() {
        String long_ = "x".repeat(WorksiteNames.MAX_LENGTH + 20);
        String cleaned = WorksiteNames.sanitise(long_);
        assertNotNull(cleaned);
        assertEquals(WorksiteNames.MAX_LENGTH, cleaned.length());
    }

    private static WorksiteKey key() {
        //? if >=1.21 {
        return new WorksiteKey(
                net.minecraft.resources.ResourceLocation.parse("townstead:anchor"),
                net.minecraft.resources.ResourceLocation.parse("minecraft:overworld"), 1L);
        //?} else {
        /*return new WorksiteKey(
                new net.minecraft.resources.ResourceLocation("townstead:anchor"),
                new net.minecraft.resources.ResourceLocation("minecraft:overworld"), 1L);
        *///?}
    }
}
