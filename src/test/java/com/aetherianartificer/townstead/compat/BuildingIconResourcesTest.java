package com.aetherianartificer.townstead.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildingIconResourcesTest {
    @Test
    void townsteadBuildingsUseOutlinedFootprintsWithSidecarItemIcons() throws Exception {
        Path extendedRoot = resourcePath("data/townstead/extended_buildings");
        checkBuildingRoot(resourcePath("data/mca/building_types"), extendedRoot);
        checkBuildingRoot(resourcePath("townstead_compat/building_types"), extendedRoot);
    }

    private static void checkBuildingRoot(Path buildingRoot, Path extendedRoot) throws IOException {
        try (var files = Files.walk(buildingRoot)) {
            for (Path buildingFile : files
                    .filter(path -> path.toString().endsWith(".json")).toList()) {
                String type = buildingRoot.relativize(buildingFile).toString().replace('\\', '/');
                type = type.substring(0, type.length() - ".json".length());
                checkBuilding(type, buildingFile, extendedRoot);
            }
        }
    }

    private static void checkBuilding(String type, Path buildingFile, Path extendedRoot)
            throws IOException {
        JsonObject building = read(buildingFile);
        assertTrue(building.has("icon"),
                type + " must advertise an icon to legacy MCA renderers");
        assertFalse(building.get("icon").getAsBoolean(),
                type + " must retain its map footprint instead of becoming an icon-only point");
        assertTrue(building.has("iconU") && building.has("iconV"),
                type + " must retain MCA's compatibility icon slot");

        Path sidecarFile = extendedRoot.resolve(type + ".json");
        assertTrue(Files.isRegularFile(sidecarFile),
                type + " requires an extended-building sidecar");
        JsonObject sidecar = read(sidecarFile);
        assertTrue(sidecar.has("catalog") && sidecar.get("catalog").isJsonObject(),
                type + " requires catalog metadata for its item icon");
        JsonElement nodeItem = sidecar.getAsJsonObject("catalog").get("node_item");
        assertNotNull(nodeItem, type + " requires catalog.node_item");
        List<JsonElement> candidates = nodeItem.isJsonArray()
                ? nodeItem.getAsJsonArray().asList() : List.of(nodeItem);
        assertFalse(candidates.isEmpty(), type + " has an empty node_item list");
        for (JsonElement candidate : candidates) {
            assertTrue(candidate.isJsonPrimitive()
                            && candidate.getAsJsonPrimitive().isString()
                            && !candidate.getAsString().isBlank(),
                    type + " has an invalid node_item candidate");
        }
    }

    private static Path resourcePath(String path) throws URISyntaxException {
        URL resource = BuildingIconResourcesTest.class.getClassLoader().getResource(path);
        assertNotNull(resource, "Missing test resource " + path);
        return Path.of(resource.toURI());
    }

    private static JsonObject read(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
