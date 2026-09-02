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
                "the outdoor form should retain its real footprint; Townstead overlays its item icon");
    }

    @Test
    void pizzaCounterIsACompactOptionalOutdoorBuilding() throws Exception {
        JsonObject extended = resource(
                "/data/townstead/extended_buildings/compat/pizzadelight/pizzeria_l1.json");
        assertEquals("optional", extended.get("enclosure").getAsString());

        JsonObject mca = resource(
                "/townstead_compat/building_types/compat/pizzadelight/pizzeria_l1.json");
        assertTrue(mca.get("margin").getAsInt() > 0);
        assertTrue(mca.get("mergeRange").getAsInt() > 0);
        assertTrue(mca.get("mergeRange").getAsInt() < mca.get("margin").getAsInt(),
                "the counter must not absorb furniture from a neighboring restaurant");
        assertFalse(mca.has("grouped"),
                "the Pizza Counter may be built either outside or as a small room");
        assertFalse(mca.get("icon").getAsBoolean(),
                "the outdoor form should retain its footprint beneath the item icon");
    }

    @Test
    void masonsYardIsAnOutdoorMaterialsStore() throws Exception {
        JsonObject extended = resource(
                "/data/townstead/extended_buildings/masons_yard.json");
        assertEquals("none", extended.get("enclosure").getAsString());
        assertFalse(extended.has("workers"),
                "the materials yard must not compete with the Mason's actual worksite");
        assertTrue(extended.getAsJsonArray("storage_roles").asList().stream()
                .anyMatch(value -> "townstead:materials".equals(value.getAsString())));

        JsonObject mca = resource("/data/mca/building_types/masons_yard.json");
        JsonObject blocks = mca.getAsJsonObject("blocks");
        assertFalse(blocks.has("minecraft:stonecutter"));
        assertTrue(blocks.get("#townstead:storage").getAsInt() >= 4,
                "a storage yard must require substantial storage capacity");
        assertTrue(blocks.get("#townstead:masonry_materials").getAsInt() >= 4);
        assertTrue(mca.get("mergeRange").getAsInt() < mca.get("margin").getAsInt(),
                "nearby yards must remain separate sites");

        JsonObject materials = resource(
                "/data/townstead/tags/block/masonry_materials.json");
        var values = materials.getAsJsonArray("values");
        assertTrue(values.asList().stream().anyMatch(value -> value.isJsonObject()
                        && "#c:stones".equals(value.getAsJsonObject().get("id").getAsString())
                        && !value.getAsJsonObject().get("required").getAsBoolean()),
                "modded common-tag stone must count as Mason's Yard material");
        assertTrue(values.asList().stream().anyMatch(value -> value.isJsonObject()
                        && "#c:concretes".equals(value.getAsJsonObject().get("id").getAsString())
                        && !value.getAsJsonObject().get("required").getAsBoolean()),
                "modded common-tag concrete must count as Mason's Yard material");
    }

    private static JsonObject resource(String path) throws Exception {
        var stream = BuildingEnclosurePoliciesTest.class.getResourceAsStream(path);
        if (stream == null) {
            stream = BuildingEnclosurePoliciesTest.class.getResourceAsStream(
                    path.replace("/tags/block/", "/tags/blocks/")
                            .replace("/tags/item/", "/tags/items/"));
        }
        if (stream == null) throw new IllegalStateException("Missing test resource " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
