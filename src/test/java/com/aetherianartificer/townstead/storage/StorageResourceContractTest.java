package com.aetherianartificer.townstead.storage;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageResourceContractTest {
    @Test
    void broadStorageAndNetworkControllersAreDataDriven() {
        JsonObject storage = resource("/data/townstead/storage_role/vanilla_storage.json");
        assertTrue(storage.getAsJsonArray("blocks").asList().stream()
                .anyMatch(value -> "#townstead:storage".equals(value.getAsString())));

        JsonObject aggregators = resource("/data/townstead/storage_role/storage_aggregators.json");
        assertTrue("not_storage".equals(aggregators.get("role").getAsString()));
        assertTrue(aggregators.getAsJsonArray("blocks").asList().stream()
                .anyMatch(value -> "#townstead:storage_aggregators".equals(value.getAsString())));
    }

    @Test
    void specialistStorageTagsReuseTheGeneralStorageContract() {
        for (String path : new String[]{
                "/data/townstead/tags/block/wool_shed_storage.json",
                "/data/townstead/tags/block/compat/butchery/butcher_shop_storage.json",
                "/data/townstead/tags/block/avoid_standing.json"}) {
            JsonObject tag = resource(path);
            assertTrue(tag.getAsJsonArray("values").asList().stream()
                    .anyMatch(value -> "#townstead:storage".equals(value.getAsString())), path);
        }
    }

    @Test
    void structuralTagsComposeBroadMaterialFamilies() {
        JsonObject dock = resource("/data/townstead/tags/block/dock_surfaces.json");
        for (String selector : new String[]{
                "#minecraft:planks", "#minecraft:slabs", "#minecraft:stairs",
                "#minecraft:wooden_trapdoors", "#townstead:masonry_materials"}) {
            assertTrue(dock.getAsJsonArray("values").asList().stream()
                    .anyMatch(value -> selector.equals(value.getAsString())), selector);
        }
        assertTrue(dock.getAsJsonArray("values").size() <= 6,
                "dock surfaces must not return to a hand-maintained block catalogue");

        JsonObject oven = resource("/data/townstead/tags/block/pizzeria/oven_masonry.json");
        assertTrue(oven.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> "#townstead:masonry_materials".equals(value.getAsString())));

        JsonObject copper = resource("/data/townstead/tags/block/kitchen/copper.json");
        assertTrue(copper.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> value.isJsonObject()
                        && "#c:storage_blocks/copper".equals(
                        value.getAsJsonObject().get("id").getAsString())));
    }

    @Test
    void storageBuildingsDeclareSemanticRoles() {
        assertRole("/data/townstead/extended_buildings/storage.json", "townstead:general");
        assertRole("/data/townstead/extended_buildings/armory.json", "townstead:equipment");
        assertRole("/data/townstead/extended_buildings/archives.json", "townstead:documents");
        assertRole("/data/townstead/extended_buildings/infirmary.json", "townstead:medical");
        assertRole("/data/townstead/extended_buildings/masons_yard.json", "townstead:materials");
    }

    @Test
    void semanticContainerRolesHaveStableDatapackTags() {
        assertContainerRole("inputs", "inputs");
        assertContainerRole("finished_goods", "finished_goods");
        assertContainerRole("tools", "tools");
        assertContainerRole("reserves", "reserves");
        assertContainerRole("personal_storage", "personal");
    }

    @Test
    void professionsPreferRolesNotContainerBlocks() {
        for (String profession : new String[]{
                "armorer", "cleric", "weaponsmith", "cartographer", "toolsmith", "fletcher", "mason"}) {
            JsonObject work = resource("/data/minecraft/profession/" + profession + "/work.json");
            JsonObject storage = work.getAsJsonObject("storage");
            assertTrue(storage.has("preferred_roles"), profession);
            assertFalse(storage.has("preferred"), profession);
            assertFalse(storage.has("buildings"), profession);
        }
    }

    private static void assertRole(String path, String role) {
        assertTrue(resource(path).getAsJsonArray("storage_roles").asList().stream()
                .anyMatch(value -> role.equals(value.getAsString())), path);
    }

    private static void assertContainerRole(String document, String tag) {
        JsonObject role = resource("/data/townstead/storage_role/" + document + ".json");
        assertTrue(role.getAsJsonArray("blocks").asList().stream()
                .anyMatch(value -> ("#townstead:storage_roles/" + tag)
                        .equals(value.getAsString())));
        resource("/data/townstead/tags/block/storage_roles/" + tag + ".json");
    }

    private static JsonObject resource(String path) {
        var stream = StorageResourceContractTest.class.getResourceAsStream(path);
        if (stream == null) {
            stream = StorageResourceContractTest.class.getResourceAsStream(
                    path.replace("/tags/block/", "/tags/blocks/")
                            .replace("/tags/item/", "/tags/items/"));
        }
        if (stream == null) throw new AssertionError("Missing test resource " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception error) {
            throw new AssertionError("Could not read " + path, error);
        }
    }
}
