package com.aetherianartificer.townstead.food;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakeryServingMenuResourceTest {
    private static final Set<String> BUILDINGS = Set.of(
            "bakery",
            "compat/bakery/bread_stand_l1",
            "compat/bakery/bake_sale_l2",
            "compat/bakery/bakery_l3");

    @Test
    void vanillaAndBakeryModContributeSeparatePlateableProducts() {
        JsonObject vanilla = resource("vanilla_bakery");
        JsonObject bakery = resource("bakery_mod");

        assertEquals(BUILDINGS, strings(vanilla, "buildings"));
        assertEquals(BUILDINGS, strings(bakery, "buildings"));
        assertEquals(Set.of("minecraft:bread", "minecraft:cookie", "minecraft:pumpkin_pie"),
                strings(vanilla, "products"));

        Set<String> products = strings(bakery, "products");
        assertEquals(23, products.size());
        assertTrue(products.contains("bakery:crusty_bread"));
        assertTrue(products.contains("bakery:grilled_bacon_sandwich"));
        assertTrue(products.contains("bakery:apple_cupcake"));
        assertFalse(products.contains("bakery:cake_dough"), "raw dough is not a menu product");
        assertFalse(products.contains("bakery:strawberry_jam"), "a jam jar is not a plated dish");
        assertFalse(products.contains("bakery:strawberry_cake"),
                "whole block-style cakes need a portion adapter before they can be plated");
    }

    private JsonObject resource(String name) {
        String path = "/data/townstead/serving_menu/" + name + ".json";
        var stream = getClass().getResourceAsStream(path);
        if (stream == null) throw new AssertionError("Missing resource " + path);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Set<String> strings(JsonObject json, String key) {
        return StreamSupport.stream(json.getAsJsonArray(key).spliterator(), false)
                .map(value -> value.getAsString())
                .collect(Collectors.toSet());
    }
}
