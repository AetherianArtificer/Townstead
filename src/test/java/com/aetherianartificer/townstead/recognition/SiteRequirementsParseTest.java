package com.aetherianartificer.townstead.recognition;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SiteRequirementsParseTest {
    private static List<SiteRequirements.Requirement> parse(String json) {
        return SiteRequirements.parse(JsonParser.parseString(json).getAsJsonArray());
    }

    @Test
    void surfaceOverTakesATagAndItsCount() {
        var requirement = assertInstanceOf(SiteRequirements.SurfaceOver.class,
                parse("[{\"surface_over\": \"#townstead:liquids\", \"count\": 24}]").get(0));
        assertEquals(24, requirement.count());
        assertEquals(6, requirement.maxDrop());
        assertEquals("townstead:liquids", requirement.tag().location().toString());
    }

    @Test
    void enclosedCarriesItsInteriorBounds() {
        var requirement = assertInstanceOf(SiteRequirements.Enclosed.class,
                parse("[{\"enclosed\": true, \"min_interior\": 4, \"max_interior\": 4096}]").get(0));
        assertEquals(4, requirement.minInterior());
        assertEquals(4096, requirement.maxInterior());
    }

    @Test
    void anEntryNamingNoKindIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("[{\"count\": 4}]"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("[{\"surface_over\": \"#townstead:liquids\", \"count\": 0}]"));
    }
}
