package com.aetherianartificer.townstead.recognition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingEnclosurePoliciesTest {
    @AfterEach
    void reset() {
        BuildingEnclosurePolicies.replaceAll(Map.of());
    }

    @Test
    void modesDescribeRoomAndOutdoorForms() {
        assertTrue(BuildingEnclosurePolicies.Mode.REQUIRED.allowsRoom());
        assertFalse(BuildingEnclosurePolicies.Mode.REQUIRED.allowsOpenAir());
        assertTrue(BuildingEnclosurePolicies.Mode.OPTIONAL.allowsRoom());
        assertTrue(BuildingEnclosurePolicies.Mode.OPTIONAL.allowsOpenAir());
        assertFalse(BuildingEnclosurePolicies.Mode.NONE.allowsRoom());
        assertTrue(BuildingEnclosurePolicies.Mode.NONE.allowsOpenAir());
        assertThrows(IllegalArgumentException.class,
                () -> BuildingEnclosurePolicies.Mode.parse("sometimes"));
    }

    @Test
    void requiredIsTheImplicitAndUnstoredDefault() {
        BuildingEnclosurePolicies.replaceAll(Map.of(
                "bakery_l3", BuildingEnclosurePolicies.Mode.REQUIRED,
                "compat/bakery/bread_stand_l1", BuildingEnclosurePolicies.Mode.OPTIONAL));

        assertEquals(BuildingEnclosurePolicies.Mode.REQUIRED,
                BuildingEnclosurePolicies.modeOf("bakery_l3"));
        assertEquals(BuildingEnclosurePolicies.Mode.OPTIONAL,
                BuildingEnclosurePolicies.modeOf("compat/bakery/bread_stand_l1"));
        assertEquals(1, BuildingEnclosurePolicies.snapshot().size());
    }

    @Test
    void breadStandOptsInAndProvidesOutdoorGroupingDistance() throws Exception {
        JsonObject extended = resource(
                "/data/townstead/extended_buildings/compat/bakery/bread_stand_l1.json");
        assertEquals("optional", extended.get("enclosure").getAsString());

        JsonObject mca = resource(
                "/townstead_compat/building_types/compat/bakery/bread_stand_l1.json");
        assertTrue(mca.get("margin").getAsInt() > 0,
                "an external building needs an interaction margin");
        assertTrue(mca.get("mergeRange").getAsInt() > 0,
                "MCA must group the complete set of reported furniture into one site");
        assertTrue(mca.get("mergeRange").getAsInt() < mca.get("margin").getAsInt(),
                "the compact stand must not absorb tagged storage from a neighboring building");
        assertFalse(mca.has("grouped"),
                "grouped would make Bread Stand outdoor-only instead of optional");
        assertFalse(mca.get("icon").getAsBoolean(),
                "the open-air form should render its footprint instead of MCA's point-only external marker");
    }

    private static JsonObject resource(String path) throws Exception {
        var stream = BuildingEnclosurePoliciesTest.class.getResourceAsStream(path);
        if (stream == null) throw new IllegalStateException("Missing test resource " + path);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
