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

class BrewinCocktailPackDataTest {
    private static final Set<String> B_AND_C_BLOCKS = Set.of(
            "brewinandchewin:keg", "brewinandchewin:item_mat");

    @Test
    void versionedCocktailProfilesKeepPlayerAndVillagerSelectorsDisjointByAudience() {
        for (String line : List.of("1201", "1211")) {
            JsonObject player = data("consumable/cocktails_delight_" + line + "_player.json");
            Set<String> villagerItems = new LinkedHashSet<>();
            for (String suffix : List.of("alcoholic", "boiler", "soft")) {
                JsonObject villager = data("consumable/cocktails_delight_" + line
                        + "_villager_" + suffix + ".json");
                assertEquals(Set.of("villager"), strings(
                        villager.getAsJsonObject("transaction").getAsJsonArray("consumers")));
                villagerItems.addAll(strings(villager.getAsJsonArray("items")));
                assertFalse(villager.toString().contains("\"alcohol\""));
            }
            assertEquals(Set.of("player"), strings(
                    player.getAsJsonObject("transaction").getAsJsonArray("consumers")));
            assertEquals(strings(player.getAsJsonArray("items")), villagerItems);
        }
    }

    @Test
    void alcoholicVillagerProfilesWriteOnlyTheOpenDrunkState() {
        for (String line : List.of("1201", "1211")) {
            for (String suffix : List.of("alcoholic", "boiler")) {
                JsonObject profile = data("consumable/cocktails_delight_" + line
                        + "_villager_" + suffix + ".json");
                JsonObject effect = profile.getAsJsonObject("effects");
                assertEquals("pheno:add_state", effect.get("type").getAsString());
                assertEquals("townstead_state:drunk", effect.get("state").getAsString());
                JsonObject admission = profile.getAsJsonObject("transaction")
                        .getAsJsonObject("effect_admission");
                assertEquals("deny", admission.get("default").getAsString());
                assertEquals(Set.of("attribute"), strings(admission.getAsJsonArray("allow")));
            }
        }
    }

    @Test
    void ambientCocktailMenuContainsOnlyAuditedUnconditionalRecipes() {
        assertEquals(Set.of("brewincompatdelight:vodka_tonic", "brewincompatdelight:boilermaker"),
                strings(data("serving_menu/cocktails_delight_1201_taproom.json")
                        .getAsJsonArray("products")));
        assertEquals(Set.of("cocktailsdelight:vodka_tonic", "cocktailsdelight:boilermaker"),
                strings(data("serving_menu/cocktails_delight_1211_taproom.json")
                        .getAsJsonArray("products")));
    }

    @Test
    void kegAndBuildingsUseOnlyAuditedPhysicalBlocks() {
        JsonObject station = data("workstation/brewin_keg.json");
        assertEquals("townstead:workstation/v2", station.get("schema").getAsString());
        assertEquals(Set.of("brewinandchewin:keg"), strings(station.getAsJsonArray("blocks")));
        JsonObject slots = station.getAsJsonObject("inventory").getAsJsonObject("slots");
        assertEquals(5, slots.getAsJsonArray("ingredients").size());
        assertEquals(6, slots.getAsJsonArray("containers").get(0).getAsInt());
        assertEquals(7, slots.getAsJsonArray("outputs").get(0).getAsInt());

        for (String building : List.of("brewhouse_l1", "brewhouse_l2", "taproom_l1")) {
            JsonObject mca = resource("/townstead_compat/building_types/compat/brewinandchewin/"
                    + building + ".json");
            for (String selector : mca.getAsJsonObject("blocks").keySet()) {
                if (selector.startsWith("brewinandchewin:")) {
                    assertTrue(B_AND_C_BLOCKS.contains(selector), "guessed block id " + selector);
                }
            }
            assertNotNull(data("extended_buildings/compat/brewinandchewin/"
                    + building + ".json"));
        }
    }

    private static JsonObject data(String path) {
        return resource("/data/townstead/" + path);
    }

    private static JsonObject resource(String path) {
        var stream = BrewinCocktailPackDataTest.class.getResourceAsStream(path);
        assertNotNull(stream, "missing pack resource: " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Set<String> strings(JsonArray values) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement value : values) out.add(value.getAsString());
        return Set.copyOf(out);
    }
}
