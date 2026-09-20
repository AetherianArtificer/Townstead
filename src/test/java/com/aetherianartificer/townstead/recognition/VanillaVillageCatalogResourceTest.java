package com.aetherianartificer.townstead.recognition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaVillageCatalogResourceTest {
    @Test
    void vanillaProfessionTypesUseTheirGeneratedWorkstations() {
        assertIngredient("armorer", "minecraft:blast_furnace", 1);
        assertIngredient("butcher", "minecraft:smoker", 1);
        assertIngredient("cartographer", "minecraft:cartography_table", 1);
        assertIngredient("fishermans_hut", "minecraft:barrel", 2);
        assertIngredient("fletcher", "minecraft:fletching_table", 1);
        assertIngredient("leatherworker", "minecraft:water_cauldron", 1);
        assertIngredient("library", "minecraft:bookshelf", 4);
        assertIngredient("library", "minecraft:lectern", 1);
        assertIngredient("mason", "minecraft:stonecutter", 1);
        assertIngredient("weaving_mill", "minecraft:loom", 1);
        assertIngredient("temple", "minecraft:brewing_stand", 1);
        assertIngredient("toolsmith", "minecraft:smithing_table", 1);
        assertIngredient("weaponsmith", "minecraft:grindstone", 1);
    }

    @Test
    void generatedOutdoorSitesHaveDistinctPhysicalSignatures() {
        assertIngredient("farm", "minecraft:composter", 1);
        assertIngredient("farm", "minecraft:farmland", 8);
        assertIngredient("stable", "minecraft:hay_block", 5);
        assertIngredient("stable", "#minecraft:fences", 17);
        assertIngredient("pen", "#townstead:pen_markers", 1);

        JsonObject stable = resource("/data/townstead/extended_buildings/stable.json");
        JsonObject pen = resource("/data/townstead/extended_buildings/pen.json");
        assertTrue(stable.getAsJsonArray("requires").get(0).getAsJsonObject().get("enclosed").getAsBoolean());
        assertTrue(pen.getAsJsonArray("requires").get(0).getAsJsonObject().get("enclosed").getAsBoolean());
    }

    @Test
    void meetingSitesUseConceptualIdsAndAreBackedByHangoutVenues() {
        Set<String> expected = Set.of("market", "grove", "pavilion", "square");
        Set<String> venueBuildings = new HashSet<>();
        for (String venue : new String[]{"market", "grove", "pavilion", "square"}) {
            JsonObject json = resource("/data/townstead/hangout_venue/village_" + venue + ".json");
            json.getAsJsonArray("buildings").forEach(value -> venueBuildings.add(value.getAsString()));
        }
        for (String type : expected) {
            JsonObject building = resource("/data/mca/building_types/" + type + ".json");
            assertTrue(building.get("visible").getAsBoolean(), type);
            assertTrue(building.get("priority").getAsInt() >= 11, type);
            assertEquals("none", resource("/data/townstead/extended_buildings/" + type + ".json")
                    .get("enclosure").getAsString(), type);
            assertTrue(venueBuildings.contains(type), type);
        }
        JsonObject market = resource("/data/mca/building_types/market.json");
        assertEquals(2, market.getAsJsonObject("blocks").size(),
                "market must not capture generic village surfaces");
        assertTrue(market.getAsJsonObject("blocks").has("#townstead:village_market_canopies"));
    }

    @Test
    void minorVillageAssembliesAndWaterFeaturesAreConceptualObjectSets() {
        for (String id : new String[]{"lamp_post", "well", "fountain", "haystack", "flower_bed"}) {
            JsonObject json = resource("/data/townstead/object_set/" + id + ".json");
            assertEquals("townstead:object_set/v1", json.get("schema").getAsString());
            assertNotNull(json.get("icon"), id);
        }
        assertTrue(resource("/data/townstead/object_set/lamp_post.json").getAsJsonArray("variants").size() >= 3);
        assertTrue(resource("/data/townstead/object_set/well.json").getAsJsonArray("variants").size() >= 4);
        assertEquals("townstead:well", resource("/data/townstead/hangout_venue/village_well.json")
                .getAsJsonArray("object_sets").get(0).getAsString());
        assertEquals("townstead:fountain", resource("/data/townstead/hangout_venue/village_fountain.json")
                .getAsJsonArray("object_sets").get(0).getAsString());
    }

    private static void assertIngredient(String type, String selector, int count) {
        JsonObject blocks = resource("/data/mca/building_types/" + type + ".json")
                .getAsJsonObject("blocks");
        assertTrue(blocks.has(selector), type + " lacks " + selector);
        assertEquals(count, blocks.get(selector).getAsInt(), type + " " + selector);
    }

    private static JsonObject resource(String path) {
        var stream = VanillaVillageCatalogResourceTest.class.getResourceAsStream(path);
        assertNotNull(stream, path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
