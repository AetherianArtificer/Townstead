package com.aetherianartificer.townstead.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingIconResourcesTest {
    @Test
    void everyNodeItemBuildingHasAUniqueRuntimeIconSlot() throws Exception {
        Path extendedRoot = resourcePath("data/townstead/extended_buildings");
        Map<Long, String> typeByRuntimeUv = new HashMap<>();

        try (var files = Files.walk(extendedRoot)) {
            for (Path extended : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject sidecar = read(extended);
                if (!sidecar.has("catalog") || !sidecar.get("catalog").isJsonObject()) continue;
                JsonObject catalog = sidecar.getAsJsonObject("catalog");
                if (!catalog.has("node_item")) continue;

                String type = extendedRoot.relativize(extended).toString().replace('\\', '/');
                type = type.substring(0, type.length() - ".json".length());
                assertFalse(catalog.get("node_item").getAsString().isBlank(), type + " has an empty node_item");

                Path buildingFile = buildingTypeResource(type);
                JsonObject building = read(buildingFile);
                int rawU = building.has("iconU") ? building.get("iconU").getAsInt() : 0;
                int rawV = building.has("iconV") ? building.get("iconV").getAsInt() : 0;
                boolean icon = building.has("icon") && building.get("icon").getAsBoolean();
                assertTrue(icon || rawU != 0 || rawV != 0, type + " has no renderable icon slot");

                int runtimeU = rawU * 20;
                int runtimeV = rawV * 60;
                long key = (((long) runtimeU) << 32) ^ (runtimeV & 0xFFFFFFFFL);
                String conflict = typeByRuntimeUv.putIfAbsent(key, type);
                assertTrue(conflict == null || conflict.equals(type),
                        type + " shares runtime icon UV with " + conflict);
            }
        }
    }

    @Test
    void hearthKitchenMapsToCampfireAtMcaRuntimeUv() throws Exception {
        JsonObject building = read(buildingTypeResource("compat/farmersdelight/kitchen_l1"));
        JsonObject sidecar = read(resourcePath(
                "data/townstead/extended_buildings/compat/farmersdelight/kitchen_l1.json"));

        assertEquals(4800, building.get("iconU").getAsInt() * 20);
        assertEquals(10800, building.get("iconV").getAsInt() * 60);
        assertEquals("minecraft:campfire",
                sidecar.getAsJsonObject("catalog").get("node_item").getAsString());
    }

    private static Path buildingTypeResource(String type) throws URISyntaxException {
        String prefix = type.startsWith("compat/")
                ? "townstead_compat/building_types/"
                : "data/mca/building_types/";
        return resourcePath(prefix + type + ".json");
    }

    private static Path resourcePath(String path) throws URISyntaxException {
        URL resource = BuildingIconResourcesTest.class.getClassLoader().getResource(path);
        assertTrue(resource != null, "processed resource is missing: " + path);
        return Path.of(resource.toURI());
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
