package com.aetherianartificer.townstead.hangout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HospitalityFurnitureDataTest {
    private static final String SEATS = "#townstead_hangouts:seats";
    private static final String TABLES = "#townstead_hangouts:tables";

    @Test
    void furnitureUmbrellasImportCommunityTagsAndKnownCompatFurniture() {
        Set<String> seats = tagValues("townstead_hangouts", "seats.json");
        assertTrue(seats.containsAll(Set.of(
                "#townstead_hangouts:stairs",
                "#townstead_hangouts:benches",
                "#townstead_hangouts:stools",
                "#c:chairs", "#c:seats", "#forge:chairs", "#forge:seats")));

        Set<String> benches = tagValues("townstead_hangouts", "benches.json");
        assertTrue(benches.containsAll(Set.of("#c:benches", "#forge:benches", "brewery:bench")));

        Set<String> tables = tagValues("townstead_hangouts", "tables.json");
        assertTrue(tables.containsAll(Set.of(
                "#c:tables", "#forge:tables", "beachparty:palm_table", "brewery:table",
                "candlelight:table", "kaleidoscope_tavern:table")));
    }

    @Test
    void hospitalityBuildingsRequireFurnitureRolesInsteadOfOneModsFurnitureSet() {
        for (String tier : List.of("brew_hall_l1", "brew_hall_l2", "brew_hall_l3")) {
            assertFurniture("brewery", tier, true);
        }
        assertFurniture("kaleidoscope_tavern", "tavern_l1", false);
        assertFurniture("kaleidoscope_tavern", "tavern_l2", true);
        assertFurniture("kaleidoscope_tavern", "tavern_l3", true);
        for (String tier : List.of("restaurant_l1", "restaurant_l2", "restaurant_l3")) {
            assertFurniture("candlelight", tier, true);
        }
        assertFurniture("beachparty", "beach_cocktail_bar_l1", false);
        assertFurniture("beachparty", "beach_cocktail_bar_l2", true);
        assertFurniture("beachparty", "beach_cocktail_bar_l3", true);
    }

    @Test
    void genericSeatDiscoveryUsesTheUmbrellaWhileNativeChairDefinitionsStayExact() {
        JsonObject generic = resource("/data/townstead/hangout_spot/stair_seat.json");
        assertEquals(List.of(SEATS), strings(generic.getAsJsonArray("blocks")));

        JsonObject beachparty = resource("/data/townstead/hangout_spot/beachparty_beach_chair.json");
        assertEquals(List.of("beachparty:beach_chair"), strings(beachparty.getAsJsonArray("blocks")));
        assertEquals("beachparty:native_chair", beachparty.get("adapter").getAsString());
    }

    private static void assertFurniture(String mod, String building, boolean requiresTable) {
        JsonObject blocks = resource("/townstead_compat/building_types/compat/" + mod + "/"
                + building + ".json").getAsJsonObject("blocks");
        assertTrue(blocks.has(SEATS), building + " should accept any tagged seat");
        assertEquals(requiresTable, blocks.has(TABLES), building + " has the wrong table contract");
        for (String key : blocks.keySet()) {
            assertFalse(key.matches("(?:beachparty|brewery|candlelight|kaleidoscope_tavern):"
                            + ".*(?:table|bench|chair|stool|sofa).*"),
                    building + " still hard-codes furniture: " + key);
        }
    }

    private static Set<String> tagValues(String namespace, String relative) {
        JsonArray values = versionedTag(namespace, relative).getAsJsonArray("values");
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement value : values) {
            ids.add(value.isJsonObject()
                    ? value.getAsJsonObject().get("id").getAsString()
                    : value.getAsString());
        }
        return Set.copyOf(ids);
    }

    private static JsonObject versionedTag(String namespace, String relative) {
        for (String family : List.of("block", "blocks")) {
            var stream = HospitalityFurnitureDataTest.class.getResourceAsStream(
                    "/data/" + namespace + "/tags/" + family + "/" + relative);
            if (stream != null) {
                return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
            }
        }
        throw new AssertionError("missing versioned block tag " + namespace + ":" + relative);
    }

    private static JsonObject resource(String path) {
        var stream = HospitalityFurnitureDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing pack resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static List<String> strings(JsonArray values) {
        return values.asList().stream().map(JsonElement::getAsString).toList();
    }
}
