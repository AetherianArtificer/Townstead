package com.aetherianartificer.townstead.snow;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reuses the bundled Ecliptic selectors and geometry as the single source of snow shapes. */
public final class SnowOverlayDefinitions {
    public static final List<String> GROUPS = List.of("townstead_blocks", "order_sheet",
            "room_ownership_tag", "room_thermometer", "calendar", "serving_plate");

    public record Shape(String model, int x, int y) {}
    public record Variant(Map<String, String> properties, Shape shape) {
        public boolean matches(Map<String, String> state) {
            return properties.entrySet().stream().allMatch(e -> e.getValue().equals(state.get(e.getKey())));
        }
    }

    private SnowOverlayDefinitions() {}

    public static Map<String, List<Variant>> load() {
        Map<String, List<Variant>> result = new LinkedHashMap<>();
        for (String group : GROUPS) {
            JsonObject targets = read("data/townstead/eclipticseasons/snow_definitions/" + group + ".json");
            String model = targets.get("mid").getAsString().split(":", 2)[1];
            JsonObject variants = read("assets/townstead/eclipticseasons/model_definitions/" + model + ".json")
                    .getAsJsonObject("variants");
            List<Variant> shapes = new ArrayList<>();
            for (var entry : variants.entrySet()) {
                Map<String, String> properties = new LinkedHashMap<>();
                if (!entry.getKey().isEmpty()) {
                    for (String part : entry.getKey().split(",")) {
                        String[] pair = part.split("=", 2);
                        properties.put(pair[0], pair[1]);
                    }
                }
                JsonObject value = entry.getValue().getAsJsonObject();
                shapes.add(new Variant(Map.copyOf(properties), new Shape(value.get("model").getAsString(),
                        value.has("x") ? value.get("x").getAsInt() : 0,
                        value.has("y") ? value.get("y").getAsInt() : 0)));
            }
            for (var block : targets.getAsJsonArray("blocks")) result.put(block.getAsString(), List.copyOf(shapes));
        }
        return Map.copyOf(result);
    }

    private static JsonObject read(String path) {
        try (var stream = SnowOverlayDefinitions.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing snow definition: " + path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read snow definition: " + path, e);
        }
    }
}
