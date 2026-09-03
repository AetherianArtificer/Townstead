package com.aetherianartificer.townstead.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BeachpartyBuildingDataTest {
    @Test
    void everyBeachpartyVenueExpressesItsDistinctCommunitySpirits() {
        assertSpirits("beach_cocktail_bar_l1", Map.of(
                "commercial", 5, "tourism", 3, "nautical", 2));
        assertSpirits("beach_cocktail_bar_l2", Map.of(
                "commercial", 8, "tourism", 6, "nautical", 3));
        assertSpirits("beach_cocktail_bar_l3", Map.of(
                "commercial", 11, "tourism", 9, "nautical", 5));
        assertSpirits("beach_club", Map.of(
                "tourism", 9, "nautical", 7, "natural", 4));
    }

    @Test
    void eachCocktailBarTierReservesOneTradeBearingSandyMerchantProprietor() {
        JsonObject provider = resource(
                "/data/townstead/career_provider/bartender_beachparty_venues.json");
        JsonObject building = provider.getAsJsonObject("contributes")
                .getAsJsonObject("profession").getAsJsonArray("poi")
                .get(0).getAsJsonObject();
        assertEquals(java.util.List.of(1, 2, 3),
                building.getAsJsonArray("slots_per_tier").asList().stream()
                        .map(value -> value.getAsInt()).toList());
        JsonObject proprietor = building.getAsJsonObject("proprietor");
        assertEquals(1, proprietor.get("slots").getAsInt());
        assertEquals("beachparty:sandymerchant",
                proprietor.getAsJsonArray("professions").get(0).getAsString());
    }

    private static void assertSpirits(String building, Map<String, Integer> expected) {
        JsonObject sidecar = resource("/data/townstead/extended_buildings/compat/beachparty/"
                + building + ".json");
        JsonObject spirits = sidecar.getAsJsonObject("spirit");
        assertNotNull(spirits, building + " is missing Community Spirit contributions");
        assertEquals(expected.size(), spirits.size(), building + " has an unexpected spirit");
        expected.forEach((id, points) -> {
            assertNotNull(spirits.get(id), building + " is missing " + id);
            assertEquals(points, spirits.get(id).getAsInt(), building + " has wrong " + id);
        });
        assertFalse(spirits.has("pastoral"),
                building + " must express its coastal destination identity, not generic pastoral value");
    }

    private static JsonObject resource(String path) {
        var stream = BeachpartyBuildingDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing pack resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }
}
