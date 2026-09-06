package com.aetherianartificer.townstead.spirit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiritResourceContractTest {
    private static final List<List<String>> UPGRADE_CHAINS = List.of(
            List.of("compat/bakery/bread_stand_l1", "compat/bakery/bake_sale_l2", "compat/bakery/bakery_l3"),
            List.of("compat/beachparty/beach_cocktail_bar_l1", "compat/beachparty/beach_cocktail_bar_l2", "compat/beachparty/beach_cocktail_bar_l3"),
            List.of("compat/brewery/brew_hall_l1", "compat/brewery/brew_hall_l2", "compat/brewery/brew_hall_l3"),
            List.of("compat/brewery/brewhouse_l1", "compat/brewery/brewhouse_l2", "compat/brewery/brewhouse_l3"),
            List.of("compat/brewinandchewin/brewhouse_l1", "compat/brewinandchewin/brewhouse_l2"),
            List.of("compat/butchery/butcher_shop_l1", "compat/butchery/butcher_shop_l2", "compat/butchery/butcher_shop_l3"),
            List.of("compat/candlelight/restaurant_l1", "compat/candlelight/restaurant_l2", "compat/candlelight/restaurant_l3"),
            List.of("compat/farmersdelight/kitchen_l1", "compat/farmersdelight/kitchen_l2", "compat/farmersdelight/kitchen_l3", "compat/farmersdelight/kitchen_l4", "compat/farmersdelight/kitchen_l5"),
            List.of("compat/herbalbrews/herb_garden_l1", "compat/herbalbrews/herb_garden_l2"),
            List.of("compat/herbalbrews/tea_house_l1", "compat/herbalbrews/tea_house_l2", "compat/herbalbrews/tea_house_l3"),
            List.of("compat/herbalbrews/witch_hut_l1", "compat/herbalbrews/witch_hut_l2"),
            List.of("compat/kaleidoscope_tavern/tavern_l1", "compat/kaleidoscope_tavern/tavern_l2", "compat/kaleidoscope_tavern/tavern_l3"),
            List.of("compat/pizzadelight/pizzeria_l1", "compat/pizzadelight/pizzeria_l2", "compat/pizzadelight/pizzeria_l3"),
            List.of("compat/rusticdelight/cafe_l1", "compat/rusticdelight/cafe_l2", "compat/rusticdelight/cafe_l3", "compat/rusticdelight/cafe_l4", "compat/rusticdelight/cafe_l5"),
            List.of("compat/vinery/winery_l1", "compat/vinery/winery_l2", "compat/vinery/winery_l3"),
            List.of("dock_l1", "dock_l2", "dock_l3")
    );

    @Test
    void everyReadoutIdentityHasNonBlankEnglish() {
        JsonObject english = resource("assets/townstead/lang/en_us.json");
        assertNonBlank(english, "townstead.spirit.readout.settlement");
        for (String mixed : Set.of("crossroads", "metropolis", "cosmopolis", "convergence")) {
            assertNonBlank(english, "townstead.spirit.mixed." + mixed);
        }
        for (SpiritRegistry.Spirit spirit : SpiritRegistry.ordered()) {
            assertFalse(spirit.displayKey().isBlank(), spirit.id() + " has a blank display key");
            assertNonBlank(english, spirit.displayKey());
            for (int tier = 1; tier <= 5; tier++) {
                assertNonBlank(english, "townstead.spirit.tier." + spirit.id() + "." + tier);
            }
        }
    }

    @Test
    void everySpiritPairHasOneNonBlankCanonicalBlendTranslation() {
        JsonObject english = resource("assets/townstead/lang/en_us.json");
        var spirits = SpiritRegistry.ordered();
        Set<String> expectedKeys = new HashSet<>();

        for (int first = 0; first < spirits.size(); first++) {
            for (int second = first + 1; second < spirits.size(); second++) {
                String key = "townstead.spirit.blend."
                        + spirits.get(first).id() + "." + spirits.get(second).id();
                expectedKeys.add(key);
                assertTrue(english.has(key), "missing blend translation: " + key);
                assertFalse(english.get(key).getAsString().isBlank(),
                        "blank blend translation: " + key);
            }
        }

        Set<String> actualKeys = new HashSet<>();
        for (String key : english.keySet()) {
            if (key.startsWith("townstead.spirit.blend.")) actualKeys.add(key);
        }
        assertEquals(66, expectedKeys.size(), "twelve spirits should have 66 unique pairs");
        assertEquals(expectedKeys, actualKeys,
                "blend translations must use registry order and cover each pair exactly once");
    }

    @Test
    void everyAuthoredBuildingSpiritIsRegisteredAndUsesPositiveIntegerPoints() {
        Path root = resourcePath("data/townstead/extended_buildings");
        int[] spiritEntries = {0};

        try (var files = Files.walk(root)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                JsonObject building = json(path);
                if (!building.has("spirit")) continue;
                assertTrue(building.get("spirit").isJsonObject(),
                        path + " must define spirit as an object");
                for (var entry : building.getAsJsonObject("spirit").entrySet()) {
                    spiritEntries[0]++;
                    assertTrue(SpiritRegistry.contains(entry.getKey()),
                            path + " uses unknown spirit " + entry.getKey());
                    JsonElement value = entry.getValue();
                    assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                            path + " spirit " + entry.getKey() + " must use a numeric value");
                    assertTrue(value.getAsString().matches("[1-9]\\d*"),
                            path + " spirit " + entry.getKey()
                                    + " must use a positive integer, found " + value);
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("failed to enumerate authored extended buildings", exception);
        }

        assertTrue(spiritEntries[0] > 0, "no authored building spirit entries were discovered");
    }

    @Test
    void everyUpgradeStrengthensAllEstablishedSpirits() {
        for (List<String> chain : UPGRADE_CHAINS) {
            JsonObject previous = null;
            int previousTotal = 0;
            String previousId = null;

            for (String id : chain) {
                JsonObject building = resource("data/townstead/extended_buildings/" + id + ".json");
                assertTrue(building.has("spirit") && building.get("spirit").isJsonObject(),
                        id + " must define Spirit progression");
                JsonObject current = building.getAsJsonObject("spirit");
                int currentTotal = current.entrySet().stream()
                        .mapToInt(entry -> entry.getValue().getAsInt())
                        .sum();

                if (previous != null) {
                    assertTrue(currentTotal > previousTotal,
                            id + " must increase total Spirit points beyond " + previousId);
                    for (var entry : previous.entrySet()) {
                        String spirit = entry.getKey();
                        assertTrue(current.has(spirit),
                                id + " drops established Spirit " + spirit + " from " + previousId);
                        assertTrue(current.get(spirit).getAsInt() > entry.getValue().getAsInt(),
                                id + " must strengthen " + spirit + " beyond " + previousId);
                    }
                }

                previous = current;
                previousTotal = currentTotal;
                previousId = id;
            }
        }
    }

    private static JsonObject resource(String path) {
        InputStream stream = SpiritResourceContractTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing resource: " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError("failed to read " + path, exception);
        }
    }

    private static Path resourcePath(String path) {
        var url = SpiritResourceContractTest.class.getClassLoader().getResource(path);
        assertNotNull(url, "missing resource directory: " + path);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new AssertionError("failed to resolve " + path, exception);
        }
    }

    private static JsonObject json(Path path) {
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException exception) {
            throw new AssertionError("failed to read " + path, exception);
        }
    }

    private static void assertNonBlank(JsonObject english, String key) {
        assertTrue(english.has(key), "missing translation: " + key);
        assertFalse(english.get(key).getAsString().isBlank(), "blank translation: " + key);
    }
}
