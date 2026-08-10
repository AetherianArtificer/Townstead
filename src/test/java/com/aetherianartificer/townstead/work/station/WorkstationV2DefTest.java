package com.aetherianartificer.townstead.work.station;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkstationV2DefTest {

    @BeforeAll
    static void registerTypedWorkstationExpressions() {
        com.aetherianartificer.townstead.pheno.selector.BlockSelectorTypes.register(
                new com.aetherianartificer.townstead.pheno.selector.types.ConnectedBlockSelectorType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.CountValueType());
    }

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
        assertTrue(def.legacyView(java.util.Set.of(id("farm_and_charm:stove")))
                .orderable().all(), "an exact block-owned recipe family needs no duplicate output tag");
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
                "farm_and_charm_bowl", "farm_and_charm_mincer", "farm_and_charm_drying",
                "meat_grinder", "pestle_and_mortar", "taxidermy_table"};
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
                "farm_and_charm/silo_copper", "butchery/meat_grinder",
                "butchery/pestle_and_mortar", "butchery/taxidermy_table"};
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

    @Test
    void rejectsUnparseableStructureAndCapacityInsteadOfKeepingRawJson() {
        assertNull(parse("{\"blocks\":[\"example:machine\"],\"structure\":{\"type\":\"pheno:not_real\"}}"));
        assertNull(parse("{\"blocks\":[\"example:machine\"],\"capacity\":{\"type\":\"pheno:not_real\"}}"));
        assertNull(parse("{\"blocks\":[\"example:machine\"],\"capacity\":{\"positions\":0,\"per_position\":1}}"));
    }

    @Test
    void parsesNamedStructureCapacityAsRealPhenoObjects() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:machine"],
                 "structure":{"type":"pheno:connected"},
                 "capacity":{"type":"pheno:count","of":"structure"}}
                """);
        assertNotNull(def);
        assertNotNull(def.structureSelector());
        assertNotNull(def.capacityValue());
    }

    @Test
    void parsesAuditedButcheryInventorySemantics() throws Exception {
        WorkstationV2Def grinder = resource("meat_grinder");
        assertEquals(List.of(0, 1, 3), grinder.ingredientSlots());
        assertEquals(List.of(2), grinder.catalystSlots());
        assertEquals(List.of(4), grinder.outputSlots());
        assertEquals(List.of(5), grinder.returnSlots());
        assertEquals(WorkstationV2Def.RecipeSlotRole.RETURN, grinder.recipeRole(4));

        RecipeIngredient ingredient = new RecipeIngredient(List.of(id("example:item")), 1);
        assertEquals(4, grinder.executableInputs(List.of(
                ingredient, ingredient, ingredient, ingredient, ingredient)).size());

        WorkstationV2Def taxidermy = resource("taxidermy_table");
        assertEquals(List.of(0, 1, 2), taxidermy.ingredientSlots());
        assertEquals(List.of(3), taxidermy.previewSlots());
    }

    @Test
    void recipeCorrectionsAreNarrowAndOptional() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:machine"],
                 "recipe_corrections":[
                   {"recipe":"example:misreported","output":"example:actual"}
                 ]}
                """);
        assertNotNull(def);
        assertEquals(id("example:actual"),
                def.correctedOutput(id("example:misreported"), id("example:reported")));
        assertEquals(id("example:reported"),
                def.correctedOutput(id("example:other"), id("example:reported")));
    }

    @Test
    void onlyFarmersDelightStoveRequiresClearGrillingArea() throws Exception {
        WorkstationV2Def farmersDelight = resource("stove");
        WorkstationV2Def farmAndCharm = resource("farm_and_charm_stove");

        assertNotNull(farmersDelight.requires());
        assertTrue(farmersDelight.requiresJson().toString().contains("pheno:block_shape"));
        assertNull(farmAndCharm.requires());
    }

    private WorkstationV2Def resource(String name) throws Exception {
        String path = "/data/townstead/workstation/" + name + ".json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return WorkstationV2Def.parse(id("test:" + name),
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject());
        }
    }
}
