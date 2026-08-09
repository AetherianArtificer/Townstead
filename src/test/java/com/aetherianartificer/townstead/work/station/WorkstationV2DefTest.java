package com.aetherianartificer.townstead.work.station;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkstationV2DefTest {

    @Test
    void insertionPlanPreservesRepeatedRecipePositions() {
        RecipeIngredient bean = new RecipeIngredient(List.of(id("rusticdelight:roasted_coffee_beans")), 1);
        DiscoveredRecipe coffee = new DiscoveredRecipe(
                id("rusticdelight:coffee"), StationType.HOT_STATION, 0,
                id("rusticdelight:coffee"), 1, 200, false,
                id("minecraft:glass_bottle"), 1,
                List.of(bean, bean, bean, bean), false, true, null);

        List<RecipeIngredient> entries = DataDrivenStationAdapter.insertionEntries(coffee);
        assertEquals(4, entries.size());
        assertTrue(entries.stream().allMatch(entry -> entry.count() == 1));
    }
    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }

    private static WorkstationV2Def parse(String json) {
        return WorkstationV2Def.parse(id("test:station"),
                JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void identityOnlyDefinitionIsValid() {
        WorkstationV2Def def = parse("""
                {"schema":"townstead:workstation/v2","mods":["farm_and_charm"],
                 "blocks":["farm_and_charm:stove"]}
                """);
        assertNotNull(def);
        assertEquals(java.util.Set.of(id("farm_and_charm:stove")), def.blocks());
        assertTrue(def.containerSlots().isEmpty());
        assertNull(def.behavior());
    }

    @Test
    void onlyExceptionalInventoryAndBehaviorFactsAreParsed() {
        WorkstationV2Def def = parse("""
                {"schema":"townstead:workstation/v2","blocks":["example:pot"],
                 "inventory":{"slots":{"containers":[7]}},
                 "behavior":{"type":"pheno:use_block","item":"tool"}}
                """);
        assertNotNull(def);
        assertEquals(List.of(7), def.containerSlots());
        assertTrue(def.behaviorUses("tool"));
        assertFalse(def.behaviorUses("ingredient"));
    }

    @Test
    void rejectsTagsAndInventedBehaviorRoles() {
        assertNull(parse("{\"blocks\":[\"#example:machines\"]}"));
        assertNull(parse("""
                {"blocks":["example:machine"],
                 "behavior":{"type":"pheno:use_block","item":"knife_dig"}}
                """));
    }

    @Test
    void everyTargetDefinitionAndBlockOwnedAssociationShips() throws Exception {
        String[] definitions = {"cooking_pot", "cutting_board", "skillet", "stove",
                "farm_and_charm_pot", "farm_and_charm_roaster", "farm_and_charm_stove",
                "farm_and_charm_bowl", "farm_and_charm_mincer", "farm_and_charm_drying"};
        for (String definition : definitions) {
            String path = "/data/townstead/workstation/" + definition + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertEquals(WorkstationV2Def.SCHEMA, json.get("schema").getAsString(), path);
                assertNotNull(WorkstationV2Def.parse(id("test:" + definition), json), path);
                assertFalse(json.has("type"), path + " must not restore station families");
                assertFalse(json.has("recipe_types"), path + " recipes belong to the block");
            }
        }

        String[] attachments = {
                "farmersdelight/cooking_pot", "farmersdelight/cutting_board",
                "farmersdelight/skillet", "farmersdelight/stove",
                "farm_and_charm/cooking_pot", "farm_and_charm/roaster",
                "farm_and_charm/stove", "farm_and_charm/crafting_bowl",
                "farm_and_charm/mincer", "farm_and_charm/silo_wood",
                "farm_and_charm/silo_copper"};
        for (String attachment : attachments) {
            String[] parts = attachment.split("/", 2);
            String path = "/data/" + parts[0] + "/tags/recipe_type/" + parts[1] + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertFalse(json.get("replace").getAsBoolean(), path);
                assertFalse(json.getAsJsonArray("values").isEmpty(), path);
            }
        }
    }
}
