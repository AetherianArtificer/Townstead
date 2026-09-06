package com.aetherianartificer.townstead.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationCoverageTest {
    private static final List<String> PLAYER_FACING_KEYS = List.of(
            "container.townstead.personal_inventory",
            "townstead.ability.source.pack",
            "townstead.dialogue.title",
            "townstead.ui.search",
            "townstead.ui.close",
            "townstead.orders.title",
            "townstead.orders.search.hint",
            "townstead.orders.fuel.any",
            "townstead.heritage.mixed");

    @Test
    void rootsAndCalendarSyncedEnglishIsAlsoAvailableToTheClient() {
        for (String namespace : List.of("townstead_roots", "townstead_calendar")) {
            JsonObject client = resource("assets/" + namespace + "/lang/en_us.json");
            JsonObject synced = resource("data/" + namespace + "/lang/en_us.json");
            for (Map.Entry<String, JsonElement> entry : synced.entrySet()) {
                assertTrue(client.has(entry.getKey()),
                        namespace + " client locale is missing " + entry.getKey());
                assertEquals(entry.getValue().getAsString(), client.get(entry.getKey()).getAsString(),
                        namespace + " English differs for " + entry.getKey());
            }
        }
    }

    @Test
    void corePlayerFacingKeysHaveNonBlankEnglish() {
        JsonObject english = resource("assets/townstead/lang/en_us.json");
        for (String key : PLAYER_FACING_KEYS) {
            assertTrue(english.has(key), "missing client translation: " + key);
            assertFalse(english.get(key).getAsString().isBlank(), "blank client translation: " + key);
        }
    }

    @Test
    void everyBundledBuildingCatalogEntryHasARealNameAndDescription() {
        JsonObject english = resource("assets/townstead/lang/en_us.json");
        Path buildingRoot = resourcePath("data/mca/building_types");
        try (var files = Files.walk(buildingRoot)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                String id = buildingRoot.relativize(path).toString().replace('\\', '/');
                id = id.substring(0, id.length() - ".json".length());
                assertNonBlankEnglish(english, "buildingType." + id, id);
                assertNonBlankEnglish(english, "buildingType." + id + ".description", id);
            }
        } catch (IOException exception) {
            throw new AssertionError("failed to enumerate bundled building types", exception);
        }
    }

    @Test
    void everyBundledCareerAndSkillIdentityUsesSyncedTranslationKeys() {
        JsonObject client = resource("assets/townstead/lang/en_us.json");
        JsonObject synced = resource("data/townstead/lang/en_us.json");
        Set<String> keys = new HashSet<>();
        for (Path path : bundledTownsteadDataJson()) {
            JsonObject json;
            try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException exception) {
                throw new AssertionError("failed to read " + path, exception);
            }
            collectCareerIdentityKeys(json, path.toString(), keys);
        }
        assertFalse(keys.isEmpty(), "no bundled Career or skill identity translations were discovered");
        for (String key : keys) {
            assertTrue(client.has(key), "missing bundled-content client translation: " + key);
            assertTrue(synced.has(key), "missing bundled-content synced translation: " + key);
            assertEquals(client.get(key).getAsString(), synced.get(key).getAsString(),
                    "bundled-content English differs for " + key);
        }
    }

    @Test
    void bundledEnglishLocalesDoNotContainBlankRealEntries() {
        for (String namespace : List.of("townstead", "townstead_calendar", "townstead_roots",
                "mca_dialogue", "mca_dialogue_athletic", "mca_dialogue_confident",
                "mca_dialogue_crabby", "mca_dialogue_flirty", "mca_dialogue_gloomy",
                "mca_dialogue_greedy", "mca_dialogue_grumpy", "mca_dialogue_introverted",
                "mca_dialogue_lazy", "mca_dialogue_odd", "mca_dialogue_peppy",
                "mca_dialogue_playful", "mca_dialogue_relaxed", "mca_dialogue_sensitive",
                "mca_dialogue_shy", "mca_dialogue_witty")) {
            JsonObject english = resource("assets/" + namespace + "/lang/en_us.json");
            for (Map.Entry<String, JsonElement> entry : english.entrySet()) {
                // A few inherited MCA personality files use "_" as a merge sentinel.
                if (entry.getKey().equals("_")) continue;
                String value = entry.getValue().getAsString();
                assertFalse(value.isBlank(),
                        namespace + " has blank English for " + entry.getKey());
                assertFalse(value.contains("[PLACEHOLDER]") || value.startsWith("PLACEHOLDER COPY:"),
                        namespace + " still has placeholder English for " + entry.getKey());
            }
        }
    }

    @Test
    void bundledSyncedEnglishLocalesDoNotContainBlankEntries() {
        for (String namespace : List.of("townstead", "townstead_calendar", "townstead_roots")) {
            JsonObject english = resource("data/" + namespace + "/lang/en_us.json");
            for (Map.Entry<String, JsonElement> entry : english.entrySet()) {
                String value = entry.getValue().getAsString();
                assertFalse(value.isBlank(),
                        namespace + " synced English has a blank value for " + entry.getKey());
                assertFalse(value.contains("[PLACEHOLDER]") || value.startsWith("PLACEHOLDER COPY:"),
                        namespace + " synced English still has placeholder copy for " + entry.getKey());
            }
        }
    }

    @Test
    void bundledEnglishLocalesDoNotSilentlyOverwriteDuplicateKeys() {
        for (String namespace : List.of("townstead", "townstead_calendar", "townstead_roots",
                "mca_dialogue", "mca_dialogue_athletic", "mca_dialogue_confident",
                "mca_dialogue_crabby", "mca_dialogue_flirty", "mca_dialogue_gloomy",
                "mca_dialogue_greedy", "mca_dialogue_grumpy", "mca_dialogue_introverted",
                "mca_dialogue_lazy", "mca_dialogue_odd", "mca_dialogue_peppy",
                "mca_dialogue_playful", "mca_dialogue_relaxed", "mca_dialogue_sensitive",
                "mca_dialogue_shy", "mca_dialogue_witty")) {
            assertUniqueKeys("assets/" + namespace + "/lang/en_us.json");
        }
        for (String namespace : List.of("townstead", "townstead_calendar", "townstead_roots")) {
            assertUniqueKeys("data/" + namespace + "/lang/en_us.json");
        }
    }

    private static JsonObject resource(String path) {
        InputStream stream = LocalizationCoverageTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing resource: " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("failed to read " + path, exception);
        }
    }

    private static Path resourcePath(String path) {
        var url = LocalizationCoverageTest.class.getClassLoader().getResource(path);
        assertNotNull(url, "missing resource: " + path);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new AssertionError("failed to resolve " + path, exception);
        }
    }

    private static void assertNonBlankEnglish(JsonObject english, String key, String owner) {
        assertTrue(english.has(key), owner + " is missing client translation " + key);
        assertFalse(english.get(key).getAsString().isBlank(),
                owner + " has blank client translation " + key);
    }

    private static void assertUniqueKeys(String path) {
        Pattern property = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");
        Set<String> keys = new HashSet<>();
        InputStream stream = LocalizationCoverageTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing resource: " + path);
        try (var reader = new java.io.BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                var matcher = property.matcher(line);
                if (matcher.find()) {
                    assertTrue(keys.add(matcher.group(1)), path + " has duplicate key " + matcher.group(1));
                }
            }
        } catch (IOException exception) {
            throw new AssertionError("failed to read " + path, exception);
        }
    }

    private static List<Path> bundledTownsteadDataJson() {
        var url = LocalizationCoverageTest.class.getClassLoader().getResource("data/townstead");
        assertNotNull(url, "missing bundled Townstead data resources");
        try {
            List<Path> paths = new ArrayList<>();
            try (var stream = Files.walk(Path.of(url.toURI()))) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(paths::add);
            }
            return paths;
        } catch (IOException | URISyntaxException exception) {
            throw new AssertionError("failed to enumerate bundled Townstead data resources", exception);
        }
    }

    private static void collectCareerIdentityKeys(JsonElement element, String resource, Set<String> keys) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectCareerIdentityKeys(child, resource, keys);
            }
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement value = entry.getValue();
            if (Set.of("display_name", "description", "name", "title").contains(entry.getKey())
                    && value.isJsonObject()
                    && (value.getAsJsonObject().has("text") || value.getAsJsonObject().has("translate"))) {
                JsonObject component = value.getAsJsonObject();
                assertTrue(component.has("translate"), resource + " has literal " + entry.getKey());
                assertFalse(component.has("text"), resource + " has embedded English in " + entry.getKey());
                String key = component.get("translate").getAsString();
                assertFalse(key.isBlank(), resource + " has blank translation key for " + entry.getKey());
                keys.add(key);
            }
            collectCareerIdentityKeys(value, resource, keys);
        }
    }
}
