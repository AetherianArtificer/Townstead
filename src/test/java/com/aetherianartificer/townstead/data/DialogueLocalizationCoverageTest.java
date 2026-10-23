package com.aetherianartificer.townstead.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueLocalizationCoverageTest {
    private static final Pattern KEY_LINE = Pattern.compile("^\\s*\"([^\"]+)\"\\s*:");
    private static final List<String> BEVERAGE_NUMBERED_POOLS = List.of(
            "dialogue.chat.beverage_artisan_request.no_worksite",
            "dialogue.chat.beverage_artisan_request.no_ingredients",
            "dialogue.chat.beverage_artisan_request.no_storage",
            "dialogue.chat.beverage_artisan_request.unreachable");
    private static final String BEVERAGE_ITEM_POOL =
            "dialogue.chat.beverage_artisan_request.no_ingredients_item";
    private static final List<String> DRUNK_POOLS = List.of(
            "dialogue.chat.townstead_drunk.tipsy_arrival",
            "dialogue.chat.townstead_drunk.tipsy_ambient",
            "dialogue.chat.townstead_drunk.drunk_arrival",
            "dialogue.chat.townstead_drunk.drunk_ambient",
            "dialogue.chat.townstead_drunk.wasted_arrival",
            "dialogue.chat.townstead_drunk.wasted_ambient",
            "dialogue.chat.townstead_drunk.service_refused",
            "dialogue.chat.townstead_drunk.recovered");

    @Test
    void everyMcaEnglishCatalogueHasUniqueNonBlankEntries() throws IOException {
        for (Path locale : mcaEnglishCatalogues()) {
            Set<String> keys = new HashSet<>();
            for (String line : Files.readAllLines(locale, StandardCharsets.UTF_8)) {
                Matcher matcher = KEY_LINE.matcher(line);
                if (matcher.find()) {
                    assertTrue(keys.add(matcher.group(1)),
                            locale + " contains duplicate key " + matcher.group(1));
                }
            }
            JsonObject json = read(locale);
            for (var entry : json.entrySet()) {
                // MCA personality packs use "_" as an optional merge sentinel.
                if (entry.getKey().equals("_")) continue;
                assertFalse(entry.getKey().isBlank(), locale + " contains a blank key");
                JsonElement value = entry.getValue();
                assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
                        locale + " has a non-string value for " + entry.getKey());
                assertFalse(value.getAsString().isBlank(), locale + " has blank English for " + entry.getKey());
            }
        }
    }

    @Test
    void beverageAndDrunkSpeechHaveEveryBundledPersonalityVoice() throws IOException {
        for (Path locale : personalityEnglishCatalogues()) {
            String personality = locale.getParent().getParent().getFileName().toString()
                    .substring("mca_dialogue_".length());
            JsonObject json = read(locale);
            for (String pool : BEVERAGE_NUMBERED_POOLS) {
                assertNonBlank(json, personality + "." + pool + "/1", locale);
            }
            assertNonBlank(json, personality + "." + BEVERAGE_ITEM_POOL, locale);
            for (String pool : DRUNK_POOLS) {
                assertNonBlank(json, personality + "." + pool + "/1", locale);
            }
        }
    }

    private static List<Path> mcaEnglishCatalogues() throws IOException {
        try (Stream<Path> directories = Files.list(bundledAssets())) {
            return directories
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("mca_dialogue"))
                    .map(path -> path.resolve("lang/en_us.json"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
    }

    private static Path bundledAssets() {
        var url = DialogueLocalizationCoverageTest.class.getClassLoader().getResource("assets");
        assertNotNull(url, "missing bundled asset resources");
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new AssertionError("failed to resolve bundled asset resources", exception);
        }
    }

    private static List<Path> personalityEnglishCatalogues() throws IOException {
        return mcaEnglishCatalogues().stream()
                .filter(path -> !path.getParent().getParent().getFileName().toString().equals("mca_dialogue"))
                .toList();
    }

    private static JsonObject read(Path locale) throws IOException {
        try (Reader reader = Files.newBufferedReader(locale, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static void assertNonBlank(JsonObject json, String key, Path locale) {
        assertTrue(json.has(key), locale + " is missing " + key);
        assertFalse(json.get(key).getAsString().isBlank(), locale + " has blank English for " + key);
    }
}
