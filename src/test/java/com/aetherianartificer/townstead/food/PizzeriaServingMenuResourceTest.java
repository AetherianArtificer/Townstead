package com.aetherianartificer.townstead.food;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PizzeriaServingMenuResourceTest {
    @Test
    void pizzeriaMenuTargetsEveryTierAndListsPizza() {
        String path = "/data/townstead/serving_menu/pizzadelight_pizzeria.json";
        var stream = getClass().getResourceAsStream(path);
        assertNotNull(stream, path);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

        assertEquals(3, json.getAsJsonArray("buildings").size());
        assertEquals("pizzadelight:pizza",
                json.getAsJsonArray("products").get(0).getAsString());
    }
}
