package com.aetherianartificer.townstead.work.feedback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledProfessionFeedbackTest {
    private static final List<String> SETTINGS = List.of(
            "/data/minecraft/profession/butcher/feedback.json",
            "/data/minecraft/profession/farmer/feedback.json",
            "/data/minecraft/profession/fisherman/feedback.json",
            "/data/minecraft/profession/leatherworker/feedback.json",
            "/data/townstead/profession/barista/feedback.json",
            "/data/townstead/profession/cook/feedback.json");
    private static final List<String> RULES = List.of(
            "/data/minecraft/profession/butcher/feedback/no_smoker.json",
            "/data/minecraft/profession/butcher/feedback/no_blood_grate.json",
            "/data/minecraft/profession/butcher/feedback/no_skinning_knife.json",
            "/data/minecraft/profession/farmer/feedback/no_seeds.json",
            "/data/minecraft/profession/fisherman/feedback/no_rod.json",
            "/data/minecraft/profession/leatherworker/feedback/no_hide.json",
            "/data/townstead/profession/barista/feedback/no_cafe.json",
            "/data/townstead/profession/cook/feedback/no_kitchen.json");

    @Test
    void bundledSidecarsUseTheirSingularSchemas() {
        SETTINGS.forEach(path -> assertSchema(path, ProfessionFeedbackDocument.SETTINGS_SCHEMA));
        RULES.forEach(path -> assertSchema(path, ProfessionFeedbackDocument.RULE_SCHEMA));
    }

    private static void assertSchema(String path, String expected) {
        InputStream input = BundledProfessionFeedbackTest.class.getResourceAsStream(path);
        assertNotNull(input, "shipped resource missing: " + path);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(expected, json.get("schema").getAsString(), path);
    }
}
