package com.aetherianartificer.townstead.snow;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class SnowOverlayDefinitionsTest {
    @Test void everyExistingBlockVariantHasExactlyOneSnowShapeRegardlessOfCoatingAndWaterlogging() throws Exception {
        var definitions = SnowOverlayDefinitions.load();
        assertEquals(16, definitions.size());
        for (var block : definitions.entrySet()) {
            String path = "assets/townstead/blockstates/" + block.getKey().split(":")[1] + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var variants = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("variants");
                for (var entry : variants.entrySet()) {
                    Map<String, String> state = new HashMap<>();
                    if (!entry.getKey().isEmpty()) for (String part : entry.getKey().split(",")) {
                        String[] pair = part.split("=", 2);
                        state.put(pair[0], pair[1]);
                    }
                    for (String coated : new String[]{"true", "false"}) {
                        state.put("snow_coated", coated);
                        state.put("waterlogged", "false");
                        var matches = block.getValue().stream().filter(v -> v.matches(state)).toList();
                        assertEquals(1, matches.size(), block.getKey() + state);
                        var shape = matches.get(0).shape();
                        var model = entry.getValue().getAsJsonObject();
                        assertEquals(model.has("x") ? model.get("x").getAsInt() : 0, shape.x());
                        assertEquals(model.has("y") ? model.get("y").getAsInt() : 0, shape.y());
                        state.put("waterlogged", "true");
                        assertTrue(matches.get(0).matches(state));
                    }
                }
            }
        }
    }
}
