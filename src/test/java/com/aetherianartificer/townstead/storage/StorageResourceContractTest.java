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
    void storageTagComposesCabinetsIntegrationsAndStorageMods() {
        JsonObject storage = resource("/data/townstead/tags/block/storage.json");
        for (String selector : new String[]{
                "#farmersdelight:cabinets",
                "#townstead:compat/integrated_storage",
                "#townstead:compat/storage_mods"}) {
            assertTrue(hasValue(storage, selector), selector);
        }

        JsonObject integrations = resource(
                "/data/townstead/tags/block/compat/integrated_storage.json");
        for (String block : new String[]{
                "farmersdelight:basket",
                "bakery:cabinet", "bakery:drawer", "bakery:wall_cabinet",
                "beachparty:palm_cabinet",
                "brewery:cabinet", "brewery:drawer", "brewery:sideboard",
                "brewery:wall_cabinet",
                "candlelight:cabinet", "candlelight:drawer", "candlelight:sideboard",
                "candlelight:bamboo_cabinet", "candlelight:bamboo_drawer",
                "vinery:dark_cherry_cabinet", "vinery:dark_cherry_drawer",
                "vinery:dark_cherry_barrel", "vinery:storage_pot",
                "butchery:freezer",
                "kaleidoscope_tavern:bar_cabinet",
                "kaleidoscope_tavern:cellar_cabinet",
                "kaleidoscope_tavern:glass_bar_cabinet"}) {
            assertTrue(hasValue(integrations, block), block);
        }

        // Recipe stations and display-only shelves are not general dump targets merely because
        // their own mods describe them as storage internally.
        for (String workstation : new String[]{
                "beachparty:mini_fridge", "brewery:barrel_main",
                "vinery:fermentation_barrel", "kaleidoscope_tavern:barrel",
                "bakery:breadbox", "vinery:wine_box"}) {
            assertFalse(hasValue(integrations, workstation), workstation);
        }
    }

    @Test
    void physicalStorageModsAreAllowedButTheirNetworksAreNot() {
        JsonObject storageMods = resource(
                "/data/townstead/tags/block/compat/storage_mods.json");
        for (String selector : new String[]{
                "#storagedrawers:drawers",
                "storagedrawers:framed_compacting_drawers_3",
                "functionalstorage:oak_1",
                "functionalstorage:framed_simple_compacting_drawer",
                "functionalstorage:armory_cabinet",
                "ironchest:iron_chest", "ironchest:trapped_obsidian_chest",
                "create:item_vault", "supplementaries:sack", "supplementaries:safe"}) {
            assertTrue(hasValue(storageMods, selector), selector);
        }

        JsonObject aggregators = resource(
                "/data/townstead/tags/block/storage_aggregators.json");
        for (String block : new String[]{
                "sophisticatedstorage:controller",
                "functionalstorage:storage_controller",
                "functionalstorage:ender_drawer",
                "storagedrawers:controller",
                "storagedrawers:controller_io"}) {
            assertTrue(hasValue(aggregators, block), block);
            assertFalse(hasValue(storageMods, block), block);
        }
    }

    @Test
    void undeclaredInventoryBlocksAreNotImplicitStorage() {
        String source = source("src/main/java/com/aetherianartificer/townstead/storage/StorageRoles.java");
        assertTrue(source.contains("if (!allowed(state)) return false;"));
        assertFalse(source.contains("isProcessingContainer(level, pos, be)) return false;"));
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
        // A dock's deck may be any material: the recipe names the furniture and the liquid,
        // and the deck is recognised by standing over that liquid.
        for (int tier = 1; tier <= 3; tier++) {
            JsonObject recipe = resource("/data/mca/building_types/dock_l" + tier + ".json");
            assertTrue(recipe.getAsJsonObject("blocks").has("#townstead:liquids"), "dock_l" + tier);
            assertTrue(recipe.getAsJsonObject("blocks").keySet().stream()
                    .noneMatch(key -> key.contains("surfaces") || key.contains("planks")), "dock_l" + tier);
            JsonObject extended = resource("/data/townstead/extended_buildings/dock_l" + tier + ".json");
            assertTrue(extended.getAsJsonArray("requires").get(0).getAsJsonObject()
                    .has("surface_over"), "dock_l" + tier);
        }

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
        assertRole("/data/townstead/extended_buildings/granary.json", "townstead:agricultural");
        assertRole("/data/townstead/extended_buildings/armory.json", "townstead:equipment");
        assertRole("/data/townstead/extended_buildings/archives.json", "townstead:documents");
        assertRole("/data/townstead/extended_buildings/infirmary.json", "townstead:medical");
        assertRole("/data/townstead/extended_buildings/masons_yard.json", "townstead:materials");
    }

    @Test
    void granaryIsAStockedStorehousePreferredByFarmers() {
        JsonObject building = resource("/data/mca/building_types/granary.json");
        JsonObject requirements = building.getAsJsonObject("blocks");
        assertTrue(requirements.get("#townstead:granary/storage").getAsInt() >= 3);
        assertTrue(requirements.get("#townstead:granary/stock").getAsInt() >= 4);
        assertTrue(building.get("noBeds").getAsBoolean());

        JsonObject storage = resource(
                "/data/townstead/tags/block/granary/storage.json");
        assertTrue(hasValue(storage, "#townstead:storage"));
        JsonObject stock = resource("/data/townstead/tags/block/granary/stock.json");
        assertTrue(hasValue(stock, "minecraft:hay_block"));
        assertTrue(hasValue(stock, "farmersdelight:rice_bale"));

        JsonObject farmer = resource("/data/minecraft/profession/farmer/work.json");
        assertTrue(farmer.getAsJsonObject("storage").getAsJsonArray("preferred_roles")
                .asList().stream().anyMatch(value ->
                        "townstead:agricultural".equals(value.getAsString())));
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
    void cuttingBoardIsAToolShelfButNotGeneralStorage() {
        JsonObject machines = resource(
                "/data/townstead/storage_role/compat_farmersdelight.json");
        assertFalse(machines.getAsJsonArray("blocks").asList().stream()
                .anyMatch(value -> "farmersdelight:cutting_board".equals(value.getAsString())));

        JsonObject tools = resource(
                "/data/townstead/storage_role/compat_farmersdelight_tools.json");
        assertTrue("tools".equals(tools.get("role").getAsString()));
        assertTrue(tools.getAsJsonArray("blocks").asList().stream()
                .anyMatch(value -> "farmersdelight:cutting_board".equals(value.getAsString())));
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

    private static boolean hasValue(JsonObject tag, String expected) {
        return tag.getAsJsonArray("values").asList().stream().anyMatch(value ->
                expected.equals(value.isJsonObject()
                        ? value.getAsJsonObject().get("id").getAsString()
                        : value.getAsString()));
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


    private static String source(String relativePath) {
        java.nio.file.Path relative = java.nio.file.Path.of(relativePath);
        java.nio.file.Path path = relative;
        for (java.nio.file.Path root = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath();
             root != null;
             root = root.getParent()) {
            java.nio.file.Path candidate = root.resolve(relative);
            if (java.nio.file.Files.isRegularFile(candidate)) {
                path = candidate;
                break;
            }
        }
        try {
            return java.nio.file.Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new AssertionError("Could not read " + path, error);
        }
    }
}
