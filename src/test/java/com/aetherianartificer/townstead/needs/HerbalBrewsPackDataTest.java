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

class HerbalBrewsPackDataTest {
    private static final Set<String> DRINKS = Set.of(
            "herbalbrews:black_tea", "herbalbrews:coffee", "herbalbrews:green_tea",
            "herbalbrews:hibiscus_tea", "herbalbrews:lavender_tea",
            "herbalbrews:milk_coffee", "herbalbrews:oolong_tea",
            "herbalbrews:rooibos_tea", "herbalbrews:yerba_mate_tea");

    @Test
    void playerAndVillagerPoliciesCoverTheSameExactNineDrinks() {
        JsonObject player = data("consumable/herbal_brews_player_native.json");
        JsonObject tea = data("consumable/herbal_brews_villager_tea.json");
        JsonObject coffee = data("consumable/herbal_brews_villager_coffee.json");

        Set<String> villager = new LinkedHashSet<>(strings(tea.getAsJsonArray("items")));
        villager.addAll(strings(coffee.getAsJsonArray("items")));
        assertEquals(DRINKS, strings(player.getAsJsonArray("items")));
        assertEquals(DRINKS, Set.copyOf(villager));
        assertEquals(Set.of("player"), consumers(player));
        assertEquals(Set.of("villager"), consumers(tea));
        assertEquals(Set.of("villager"), consumers(coffee));
        assertEquals("observe_native", transaction(player).get("mode").getAsString());
        assertEquals("consume_one", transaction(player).get("accounting").getAsString());
        for (JsonObject profile : List.of(tea, coffee)) {
            assertEquals("replace_with_pheno", transaction(profile).get("mode").getAsString());
            assertEquals("deny", transaction(profile).getAsJsonObject("effect_admission")
                    .get("default").getAsString());
            assertFalse(profile.toString().contains("herbalbrews:bonding"));
            assertFalse(profile.toString().contains("pheno:add_state"));
        }
    }

    @Test
    void baristaProviderOwnsKettleWorkWithoutCreatingTeaBrewerOrWitchWork() {
        JsonObject provider = data("career_provider/barista_herbal_brews.json");
        assertEquals("townstead:beverage_artisan", provider.get("profession").getAsString());
        assertEquals("barista", provider.get("path").getAsString());
        assertEquals("herbalbrews", provider.get("mods").getAsString());
        JsonObject path = provider.getAsJsonObject("contributes").getAsJsonObject("path");
        JsonObject work = path.getAsJsonArray("work").get(0).getAsJsonObject();
        assertEquals("path", work.get("access").getAsString());
        assertEquals(Set.of("herbalbrews:tea_kettle", "herbalbrews:copper_tea_kettle"),
                strings(work.getAsJsonArray("workstations")));
        assertEquals(Set.of("#townstead:compat/herbalbrews/drinks"),
                strings(work.getAsJsonArray("recipes")));
        String text = provider.toString().toLowerCase();
        assertFalse(text.contains("tea_brewer"));
        assertFalse(text.contains("cauldron"));
        assertFalse(text.contains("flask"));
        assertFalse(text.contains("witch"));
    }

    @Test
    void kettleContractUsesNativeInventoryAndProjectsStablePublicGetters() {
        JsonObject station = data("workstation/herbal_brews_kettles.json");
        assertEquals(Set.of("herbalbrews:tea_kettle", "herbalbrews:copper_tea_kettle"),
                strings(station.getAsJsonArray("blocks")));
        JsonObject slots = station.getAsJsonObject("inventory").getAsJsonObject("slots");
        assertEquals(List.of(0), integers(slots.getAsJsonArray("outputs")));
        assertFalse(slots.has("ingredients"), "native sided insertion must choose slots 1-5");
        assertFalse(slots.has("containers"), "the bottle remains a native recipe ingredient");
        assertFalse(station.has("supplies"),
                "water and heat supplies need component-aware, conditional counted conversions");
        assertFalse(station.toString().contains("small_water_fill"),
                "the broad native tag includes component-bearing potion items");

        JsonObject projection = data("recipe_projection/herbal_brews_kettle.json");
        assertEquals("beverage.kettle", projection.get("domain").getAsString());
        assertEquals(Set.of("herbalbrews:kettle_brewing"),
                strings(projection.getAsJsonArray("recipe_types")));
        JsonObject fields = projection.getAsJsonObject("fields");
        assertEquals("getRequiredWater", fields.getAsJsonObject("fluid_amount")
                .get("path").getAsString());
        assertEquals("getRequiredHeat", fields.getAsJsonObject("environment")
                .get("path").getAsString());
        assertEquals("getRequiredDuration", fields.getAsJsonObject("time")
                .get("path").getAsString());
        assertEquals("getResultItem", fields.getAsJsonObject("output")
                .get("path").getAsString());

        for (String kettle : List.of("tea_kettle", "copper_tea_kettle")) {
            JsonObject association = resource("/data/herbalbrews/tags/recipe_type/"
                    + kettle + ".json");
            JsonObject entry = association.getAsJsonArray("values").get(0).getAsJsonObject();
            assertEquals("herbalbrews:kettle_brewing", entry.get("id").getAsString());
            assertFalse(entry.get("required").getAsBoolean());
        }
    }

    @Test
    void consolidatedTeaHouseMenuAndVenueCoverAllHerbalBrewsDrinks() {
        JsonObject teaMenu = data("serving_menu/herbal_brews_tea_house.json");
        assertEquals(DRINKS, strings(teaMenu.getAsJsonArray("products")));
        String menuText = teaMenu.toString().toLowerCase();
        assertFalse(menuText.contains("jug"));
        assertFalse(menuText.contains("flask"));
        assertFalse(menuText.contains("cauldron"));

        JsonObject teaVenue = data("hangout_venue/herbal_brews_tea_house.json");
        assertEquals(strings(teaMenu.getAsJsonArray("buildings")),
                strings(teaVenue.getAsJsonArray("buildings")));
        for (String activity : List.of("herbal_brews_coffee_break", "herbal_brews_quiet_tea",
                "herbal_brews_tasting", "herbal_brews_cafe_conversation")) {
            JsonObject json = data("hangout_activity/" + activity + ".json");
            String text = json.toString().toLowerCase();
            assertTrue(text.contains("townstead:"));
            assertFalse(text.contains("emotecraft"));
            assertFalse(text.contains("bbmodel"));
            assertFalse(text.contains("animation_json"));
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
        var stream = HerbalBrewsPackDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing Herbal Brews pack resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement value : values) out.add(value.getAsString());
        return Set.copyOf(out);
    }

    private static List<Integer> integers(JsonArray values) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (JsonElement value : values) out.add(value.getAsInt());
        return List.copyOf(out);
    }
}
