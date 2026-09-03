package com.aetherianartificer.townstead.needs;

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

class VineryBreweryPackDataTest {
    private static final Set<String> VINERY_BUILDING_BLOCKS = Set.of(
            "vinery:fermentation_barrel", "vinery:apple_press", "vinery:grapevine_pot",
            "vinery:storage_pot", "vinery:wine_box");
    private static final Set<String> BREWERY_BUILDING_BLOCKS = Set.of(
            "brewery:wooden_brewingstation", "brewery:copper_brewingstation",
            "brewery:netherite_brewingstation", "brewery:brew_oven", "brewery:brew_timer",
            "brewery:brew_whistle", "brewery:dried_barley", "brewery:cabinet",
            "brewery:barrel_main", "brewery:bar_counter", "brewery:table", "brewery:bench",
            "brewery:sideboard", "brewery:wall_cabinet");

    @Test
    void playerAndVillagerDrinkProfilesAreDisjointAndVillagersWriteOnlyDrunk() {
        for (String family : List.of("vinery_wine", "brewery_drink")) {
            JsonObject player = data("consumable/" + family + "_player.json");
            JsonObject villager = data("consumable/" + family + "_villager.json");
            assertEquals(strings(player.getAsJsonArray("items")), strings(villager.getAsJsonArray("items")));
            assertEquals(Set.of("player"), consumers(player));
            assertEquals(Set.of("villager"), consumers(villager));
            assertEquals("observe_native", transaction(player).get("mode").getAsString());
            assertEquals("replace_with_pheno", transaction(villager).get("mode").getAsString());
            JsonObject effect = villager.getAsJsonObject("effects");
            assertEquals("pheno:add_state", effect.get("type").getAsString());
            assertEquals("townstead_state:drunk", effect.get("state").getAsString());
            assertEquals("deny", transaction(villager).getAsJsonObject("effect_admission")
                    .get("default").getAsString());
        }
    }

    @Test
    void breweryProductionUsesOnlyRealStationsAndBoundedNativeResponses() {
        JsonObject provider = data("career_provider/brewer_brewery.json");
        String providerText = provider.toString();
        assertFalse(providerText.contains("brewery:barrel_main"));
        assertTrue(providerText.contains("#townstead:compat/brewery/wood_drinks"));
        assertTrue(providerText.contains("#townstead:compat/brewery/copper_drinks"));

        JsonObject station = data("workstation/brewery_brewing_stations.json");
        assertEquals(Set.of("brewery:wooden_brewingstation", "brewery:copper_brewingstation",
                "brewery:netherite_brewingstation"), strings(station.getAsJsonArray("blocks")));
        assertEquals("brewery:beer_mug", station.getAsJsonObject("collect").get("tool").getAsString());
        JsonArray incidents = station.getAsJsonObject("attendance").getAsJsonArray("incidents");
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement element : incidents) {
            JsonObject incident = element.getAsJsonObject();
            ids.add(incident.get("id").getAsString());
            assertTrue(incident.get("max_attempts").getAsInt() <= 8);
            assertEquals("pheno:use_block", incident.getAsJsonObject("response")
                    .get("type").getAsString());
        }
        assertEquals(Set.of("catch_overflow", "refill_drain", "feed_weak_oven", "reset_timer"), ids);
    }

    @Test
    void nativeWinemakerIsEnrichedWithoutInventingVintnerWork() {
        JsonObject provider = data("career_provider/winemaker_vinery.json");
        assertEquals("vinery:winemaker", provider.get("profession").getAsString());
        assertFalse(provider.toString().toLowerCase().contains("vintner"));
        assertFalse(provider.getAsJsonObject("contributes").getAsJsonObject("profession").has("tasks"),
                "cross-version Vinery inventory and juice staging are intentionally not simulated");
        JsonObject projection = data("recipe_projection/vinery_wine_fermentation.json");
        JsonObject fields = projection.getAsJsonObject("fields");
        assertTrue(fields.has("input_fluid"));
        assertTrue(fields.has("fluid_amount"));
        assertTrue(fields.has("container"));
    }

    @Test
    void winemakerPrefersTheDedicatedWineCellar() {
        JsonObject work = resource("/data/vinery/profession/winemaker/work.json");
        assertEquals("townstead:wine", work.getAsJsonObject("storage")
                .getAsJsonArray("preferred_roles").get(0).getAsString());

        JsonObject cellar = data("extended_buildings/compat/vinery/wine_cellar.json");
        assertTrue(cellar.getAsJsonArray("storage_roles").asList().stream()
                .anyMatch(value -> "townstead:wine".equals(value.getAsString())));
        assertFalse(cellar.has("workers"), "a Wine Cellar is storage, not another worksite");
    }

    @Test
    void brewerPathPrefersTheDedicatedCaskCellar() {
        JsonObject path = resource("/data/townstead/profession/beverage_artisan/path/brewer/path.json");
        assertEquals("townstead:brewed_drinks", path.getAsJsonObject("storage")
                .getAsJsonArray("preferred_roles").get(0).getAsString());

        JsonObject cellar = data("extended_buildings/compat/brewery/cask_cellar.json");
        assertTrue(cellar.getAsJsonArray("storage_roles").asList().stream()
                .anyMatch(value -> "townstead:brewed_drinks".equals(value.getAsString())));
        assertFalse(cellar.has("workers"), "a Cask Cellar is storage, not another worksite");
    }

    @Test
    void buildingsContainOnlyIdsAuditedOnBothSupportedVersions() {
        checkBuildings("vinery", List.of("wine_cellar", "winery_l1", "winery_l2", "winery_l3"),
                VINERY_BUILDING_BLOCKS);
        checkBuildings("brewery", List.of("cask_cellar", "brewhouse_l1", "brewhouse_l2", "brewhouse_l3",
                        "brew_hall_l1", "brew_hall_l2", "brew_hall_l3"),
                BREWERY_BUILDING_BLOCKS);
    }

    @Test
    void appraisalIsScopedToTheAuditedBreweryDrinkTag() {
        JsonObject appraisal = data("output_appraisal/compat/brewery_quality.json");
        assertEquals(Set.of("#townstead:compat/brewery/drinks"),
                strings(appraisal.getAsJsonArray("items")));
        assertEquals("brewery.beer_quality", appraisal.getAsJsonArray("path").get(0).getAsString());
        JsonObject drinks = resourceEither(
                "/data/townstead/tags/item/compat/brewery/drinks.json",
                "/data/townstead/tags/items/compat/brewery/drinks.json");
        assertEquals(16, drinks.getAsJsonArray("values").size());
        assertTrue(drinks.toString().contains("brewery:whiskey_carrasconlabel"));
        assertFalse(drinks.toString().contains("whiskey_hadarilabel"));
    }

    private static void checkBuildings(String mod, List<String> names, Set<String> audited) {
        for (String name : names) {
            JsonObject mca = resource("/townstead_compat/building_types/compat/" + mod + "/"
                    + name + ".json");
            for (String selector : mca.getAsJsonObject("blocks").keySet()) {
                if (selector.startsWith(mod + ":")) {
                    assertTrue(audited.contains(selector), name + " guessed block id " + selector);
                }
            }
            assertNotNull(data("extended_buildings/compat/" + mod + "/" + name + ".json"));
        }
    }

    private static JsonObject transaction(JsonObject profile) {
        return profile.getAsJsonObject("transaction");
    }

    private static Set<String> consumers(JsonObject profile) {
        return strings(transaction(profile).getAsJsonArray("consumers"));
    }

    private static JsonObject data(String path) {
        return resource("/data/townstead/" + path);
    }

    private static JsonObject resource(String path) {
        var stream = VineryBreweryPackDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing pack resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static JsonObject resourceEither(String current, String legacy) {
        var stream = VineryBreweryPackDataTest.class.getResourceAsStream(current);
        if (stream == null) stream = VineryBreweryPackDataTest.class.getResourceAsStream(legacy);
        assertNotNull(stream, "missing pack resource: " + current + " or " + legacy);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement value : values) out.add(value.getAsString());
        return Set.copyOf(out);
    }
}
