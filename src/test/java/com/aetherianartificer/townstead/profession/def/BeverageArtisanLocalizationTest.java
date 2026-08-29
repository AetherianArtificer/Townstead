package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeverageArtisanLocalizationTest {
    private static final List<String> CAREER_KEYS = List.of(
            "career.townstead.beverage_artisan",
            "entity.minecraft.villager.townstead.beverage_artisan",
            "career.townstead.beverage_artisan.description",
            "career.townstead.beverage_artisan.path.barista",
            "career.townstead.beverage_artisan.title.barista",
            "skill.townstead.beverage_artisan.barista.coffee_service",
            "skill.townstead.beverage_artisan.barista.coffee_service.description");

    @Test
    void beverageArtisanCareerKeysReachClientAndServerSyncedEnglish() {
        JsonObject client = resource("assets/townstead/lang/en_us.json");
        JsonObject synced = resource("data/townstead/lang/en_us.json");
        for (String key : CAREER_KEYS) {
            assertTrue(client.has(key), "missing client translation: " + key);
            assertTrue(synced.has(key), "missing server-synced translation: " + key);
        }
    }

    @Test
    void everyBundledClientLocaleCanRenderTheCareerTree() {
        for (String locale : List.of("de_de", "en_us", "fr_fr", "ru_ru", "uk_ua", "zh_cn")) {
            JsonObject client = resource("assets/townstead/lang/" + locale + ".json");
            for (String key : CAREER_KEYS) {
                assertTrue(client.has(key), locale + " is missing " + key);
            }
        }
    }

    private static JsonObject resource(String path) {
        InputStream stream = BeverageArtisanLocalizationTest.class.getClassLoader()
                .getResourceAsStream(path);
        assertNotNull(stream, "missing resource: " + path);
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("failed to read " + path, exception);
        }
    }
}
