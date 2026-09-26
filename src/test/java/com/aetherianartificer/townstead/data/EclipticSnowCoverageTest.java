package com.aetherianartificer.townstead.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class EclipticSnowCoverageTest {
    private JsonObject read(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test void allTargetsResolveExplicitOverlayModelsForEveryBlockState() throws Exception {
        var blocks = new HashSet<String>();
        for (String group : new String[]{"townstead_blocks", "order_sheet", "room_ownership_tag",
                "room_thermometer", "calendar", "serving_plate"}) {
            String path = "/townstead/eclipticseasons/snow_definitions/" + group + ".json";
            JsonObject definition = read("data" + path);
            assertEquals(definition, read("assets" + path));
            assertEquals(1000, definition.get("flag").getAsInt());
            assertTrue(definition.get("snow_passable").getAsBoolean());
            String mid = definition.get("mid").getAsString().split(":")[1];
            JsonObject model = read("assets/townstead/eclipticseasons/model_definitions/" + mid + ".json");
            assertFalse(model.get("replace").getAsBoolean(), "Snow must not hide the original block");
            var snowVariants = model.getAsJsonObject("variants");
            for (var entry : definition.getAsJsonArray("blocks")) {
                String id = entry.getAsString();
                assertTrue(blocks.add(id));
                var variants = read("assets/townstead/blockstates/" + id.split(":")[1] + ".json").getAsJsonObject("variants");
                assertEquals(variants.keySet(), snowVariants.keySet());
                for (var variant : variants.entrySet()) {
                    var snow = snowVariants.getAsJsonObject(variant.getKey());
                    for (String rotation : new String[]{"x", "y"})
                        assertEquals(variant.getValue().getAsJsonObject().get(rotation), snow.get(rotation));
                    var geometry = read("assets/townstead/models/" + snow.get("model").getAsString().split(":")[1] + ".json");
                    assertFalse(geometry.getAsJsonArray("elements").isEmpty());
                    for (var cap : geometry.getAsJsonArray("elements")) {
                        var element = cap.getAsJsonObject();
                        double thickness = element.getAsJsonArray("to").get(1).getAsDouble()
                                - element.getAsJsonArray("from").get(1).getAsDouble();
                        assertTrue(thickness > 0 && thickness < 1, "Only thin snow caps, not a full cube");
                    }
                }
            }
        }
        assertEquals(16, blocks.size());
        assertTrue(blocks.contains("townstead:field_post"));
        assertTrue(blocks.contains("townstead:room_thermometer"));
    }
}
