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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkstationV2DefTest {

    @BeforeAll
    static void registerTypedWorkstationExpressions() {
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EntityTypeConditionType("pheno:in_tag"));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:tameable", ctx -> true));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:tamed", ctx -> true));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:and",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.AND));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:or",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.OR));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:not",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.NOT));
        com.aetherianartificer.townstead.pheno.selector.SelectorTypes.register(
                new com.aetherianartificer.townstead.pheno.selector.types.VillageSelectorType());
        com.aetherianartificer.townstead.pheno.selector.BlockSelectorTypes.register(
                new com.aetherianartificer.townstead.pheno.selector.types.ConnectedBlockSelectorType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.CountValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.IfValueType());
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

    @Test
    void stationContentsCanRecoverMincerRecipeWithoutSession() {
        RecipeIngredient wheat = new RecipeIngredient(List.of(id("minecraft:wheat")), 1);
        assertTrue(DataDrivenStationAdapter.contentsMatchRecipe(
                Map.of(id("minecraft:wheat"), 2), List.of(wheat)));
        assertFalse(DataDrivenStationAdapter.contentsMatchRecipe(
                Map.of(id("minecraft:coal"), 1), List.of(wheat)));
    }

    @Test
    void stationContentsRequireTheWholeBowlRecipe() {
        List<RecipeIngredient> oatmeal = List.of(
                new RecipeIngredient(List.of(id("minecraft:milk_bucket")), 1),
                new RecipeIngredient(List.of(id("farm_and_charm:oat")), 1),
                new RecipeIngredient(List.of(id("farm_and_charm:strawberry")), 1));
        assertTrue(DataDrivenStationAdapter.contentsMatchRecipe(Map.of(
                id("minecraft:milk_bucket"), 1,
                id("farm_and_charm:oat"), 1,
                id("farm_and_charm:strawberry"), 1), oatmeal));
        assertFalse(DataDrivenStationAdapter.contentsMatchRecipe(Map.of(
                id("minecraft:milk_bucket"), 1,
                id("farm_and_charm:oat"), 1), oatmeal));
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
        assertEquals(ShiftEndPolicy.FINISH, def.shiftEnd());
        assertTrue(def.legacyView(java.util.Set.of(id("farm_and_charm:stove")))
                .orderable().all(), "an exact block-owned recipe family needs no duplicate output tag");
    }

    @Test
    void onlyExceptionalInventoryAndBehaviorFactsAreParsed() {
        WorkstationV2Def def = parse("""
                {"schema":"townstead:workstation/v2","blocks":["example:pot"],
                 "inventory":{"slots":{"containers":[7]}},
                 "behavior":{"type":"pheno:use_block","item":"tool"},
                 "collect":{"type":"pheno:use_block",
                    "condition":{"type":"pheno:block_state","property":"busy","value":"false"}}}
                """);
        assertNotNull(def);
        assertEquals(List.of(7), def.containerSlots());
        assertTrue(def.behaviorUses("tool"));
        assertFalse(def.behaviorUses("ingredient"));
        assertNotNull(def.collect());
    }

    @Test
    void rejectsTagsAndInventedBehaviorRoles() {
        assertNull(parse("{\"blocks\":[\"#example:machines\"]}"));
        assertNull(parse("""
                {"blocks":["example:machine"],
                 "behavior":{"type":"pheno:use_block","item":"knife_dig"}}
                """));
        assertNull(parse("""
                {"blocks":["example:machine"],
                 "collect":{"type":"pheno:use_block",
                    "condition":{"type":"pheno:not_a_real_condition"}}}
                """));
    }

    @Test
    void parsesShiftEndPolicyAndRejectsUnknownValues() {
        WorkstationV2Def leave = parse("""
                {"blocks":["example:oven"],"shift_end":"leave"}
                """);
        assertNotNull(leave);
        assertEquals(ShiftEndPolicy.LEAVE, leave.shiftEnd());
        assertEquals(ShiftEndPolicy.LEAVE, leave.legacyView(java.util.Set.of()).shiftEnd());
        assertNull(parse("""
                {"blocks":["example:oven"],"shift_end":"eventually"}
                """));
    }

    @Test
    void entityPoweredStationUsesScopedPhenoReservationAndPureRequirement() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:mill"],
                 "reservation":{"type":"pheno:reserve","on":{
                   "type":"pheno:village",
                   "where":{"type":"pheno:in_tag","tag":"example:mill_bindable"},
                   "order":"nearest","limit":1}},
                 "requires":{"type":"pheno:block_entity","property":"entity","value":true}}
                """);
        assertNotNull(def);
        assertTrue(def.hasReservation());
        assertNotNull(def.reservation());
        assertNull(parse("""
                {"blocks":["example:mill"],"reservation":{
                  "type":"pheno:reserve","on":{"type":"pheno:not_real"}}}
                """));
        assertNull(parse("""
                {"blocks":["example:mill"],"requires":{"type":"pheno:entity_engagement",
                  "on":{"type":"pheno:village"}}}
                """));
        assertNull(parse("""
                {"blocks":["example:mill"],"driver":{"entities":"#example:old_shape"}}
                """));
    }

    @Test
    void entityPoweredStationCanPreferVillageParticipantThenFallBackToWorker() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:mill"],
                 "anchor":{"radius":[1,0,1],"where":{
                   "type":"pheno:block_state","property":"part","value":"center"}},
                 "reservation":{"type":"pheno:reserve","on":[
                   {"type":"pheno:village","where":{
                     "type":"pheno:in_tag","tag":"example:work_animals"},
                    "order":"nearest","limit":1},
                   "origin"]}}
                """);
        assertNotNull(def);
        assertNotNull(def.anchorSelector());
        assertNotNull(def.reservation());
        assertTrue(def.reservation().referencesOrigin());
    }

    @Test
    void everyTargetDefinitionAndBlockOwnedAssociationShips() throws Exception {
        String[] definitions = {"cooking_pot", "cutting_board", "skillet", "stove",
                "farm_and_charm_pot", "farm_and_charm_roaster", "farm_and_charm_stove",
                "farm_and_charm_bowl", "farm_and_charm_mincer", "farm_and_charm_drying",
                "kaleidoscope_cookery_chopping_board", "kaleidoscope_cookery_pot",
                "kaleidoscope_cookery_stockpot", "kaleidoscope_cookery_millstone",
                "kaleidoscope_cookery_steamer",
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
                "farm_and_charm/silo_copper", "kaleidoscope_cookery/chopping_board",
                "kaleidoscope_cookery/pot", "kaleidoscope_cookery/stockpot",
                "kaleidoscope_cookery/millstone", "kaleidoscope_cookery/steamer",
                "butchery/meat_grinder",
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
    void kitchenRolesAreNeutralAndLegacyTagsAreAliasesOnly() throws Exception {
        String[] roles = {"advanced_heat", "cookware", "copper", "heat_sources", "pots",
                "preparation", "storage", "upgraded_storage", "nether_storage", "workstations"};
        for (String role : roles) {
            String path = "/data/townstead/tags/block/kitchen/" + role + ".json";
            try (var stream = blockTag(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                assertFalse(json.getAsJsonArray("values").isEmpty(), path);
            }
        }

        String[][] aliases = {
                {"kitchen_advanced_heat", "advanced_heat"}, {"kitchen_cookware", "cookware"},
                {"kitchen_copper", "copper"}, {"kitchen_heat_sources", "heat_sources"},
                {"kitchen_pot", "pots"}, {"kitchen_prep_blocks", "preparation"},
                {"kitchen_storage", "storage"}, {"kitchen_storage_upgraded", "upgraded_storage"},
                {"kitchen_storage_nether", "nether_storage"}, {"kitchen_workstations", "workstations"}
        };
        for (String[] alias : aliases) {
            String path = "/data/townstead/tags/block/compat/farmersdelight/" + alias[0] + ".json";
            try (var stream = blockTag(path)) {
                assertNotNull(stream, path);
                var values = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("values");
                assertEquals(1, values.size(), path + " must contain no provider blocks");
                assertEquals("#townstead:kitchen/" + alias[1], values.get(0).getAsString());
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
    void parsesConditionDrivenLaneCapacity() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:steamer"],
                 "capacity":{"positions":{"type":"pheno:if",
                   "condition":{"type":"pheno:block_state","property":"half","value":"true"},
                   "then":4,"else":8},"per_position":1}}
                """);
        assertNotNull(def);
        assertNotNull(def.capacityPositionsValue());
        assertEquals(1, def.capacityPerPosition());
        assertFalse(def.stackPerPosition());
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
    void parsesEventDrivenReadinessAndFixedSupplies() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:wok"],"supplies":["#example:oils"],
                 "ready":{"type":"pheno:block_entity","property":"status","value":2},
                 "behavior":[
                   {"type":"pheno:use_block","item":"supply","supply":"#example:oils"},
                   {"type":"pheno:use_block","item":"ingredient","all":true}
                 ],
                 "collect":{"type":"pheno:use_block","item":"container"}}
                """);
        assertNotNull(def);
        assertNotNull(def.ready());
        assertEquals(List.of("#example:oils"), def.recipeSupplies());
        assertTrue(def.behaviorUses("supply"));
        assertTrue(def.collectUses("container"));
    }

    @Test
    void parsesMixedCarrierAndSecondaryUseToolCollection() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:wok"],
                 "collect":[
                   {"type":"pheno:use_block","item":"container"},
                   {"type":"pheno:use_block","item":"tool","tool":"#example:spatulas",
                    "secondary_use":true}
                 ]}
                """);
        assertNotNull(def);
        assertTrue(def.collectUses("container"));
        assertTrue(def.collectUses("tool"));
        assertNull(parse("""
                {"blocks":["example:wok"],
                 "collect":{"type":"pheno:use_block","secondary_use":"yes"}}
                """));
    }

    @Test
    void nestedOffsetInteractionIsADataDrivenPreparationAction() {
        WorkstationV2Def def = parse("""
                {"blocks":["example:pot"],
                 "requires":{"type":"pheno:offset","y":-1,
                   "condition":{"type":"pheno:block_state","property":"lit","value":"true"}},
                 "behavior":[
                   {"type":"pheno:offset","y":-1,
                    "block_action":{"type":"pheno:use_block","item":"supply",
                      "supply":"#example:igniters",
                      "condition":{"type":"pheno:block_state","property":"lit","value":"false"}}},
                   {"type":"pheno:use_block","item":"ingredient"}
                 ]}
                """);

        assertNotNull(def);
        assertTrue(def.behaviorUses("supply"));
        assertTrue(def.behaviorUses("ingredient"));
        assertTrue(def.hasPreparationAction());
        JsonObject wrapper = def.behavior().getAsJsonArray().get(0).getAsJsonObject();
        JsonObject interaction = WorkstationV2Def.interactionOf(wrapper);
        assertNotNull(interaction);
        assertEquals("#example:igniters", interaction.get("supply").getAsString());
    }

    @Test
    void kaleidoscopeHeatStationsDeclareAutomaticIgnition() throws Exception {
        for (String station : List.of("kaleidoscope_cookery_pot",
                "kaleidoscope_cookery_stockpot", "kaleidoscope_cookery_steamer")) {
            WorkstationV2Def def = resource(station);
            assertTrue(def.hasPreparationAction(), station);
            assertTrue(def.behaviorUses("supply"), station);
            assertTrue(def.behavior().toString().contains("#kaleidoscope_cookery:lit_stove"), station);
        }
    }

    @Test
    void onlyFarmersDelightStoveRequiresClearGrillingArea() throws Exception {
        WorkstationV2Def farmersDelight = resource("stove");
        WorkstationV2Def farmAndCharm = resource("farm_and_charm_stove");

        assertNotNull(farmersDelight.requires());
        assertTrue(farmersDelight.requiresJson().toString().contains("pheno:block_shape"));
        assertNull(farmAndCharm.requires());
    }

    @Test
    void repeatableWorkDistinguishesProcessingFromPreparation() throws Exception {
        WorkstationV2Def bowl = resource("farm_and_charm_bowl");
        assertTrue(bowl.hasRepeatableWorkAction());
        assertNotNull(bowl.collect(),
                "the bowl must collect through its resetting player interaction");
        assertTrue(bowl.behavior().isJsonArray(),
                "the bowl declares terminal-state recovery as data, not mod-specific Java");
        WorkstationV2Def mincer = resource("farm_and_charm_mincer");
        assertTrue(mincer.hasRepeatableWorkAction());
        assertEquals(1, mincer.capacityPositions());
        assertTrue(mincer.stackPerPosition(),
                "the mincer consumes repeated operations from one real input stack");
        assertFalse(resource("farm_and_charm_drying").hasRepeatableWorkAction(),
                "closing a silo before loading is preparation, not a processing pulse");
    }

    @Test
    void fuelInferenceRequiresAHorizontalOnlyInventoryChannel() {
        assertArrayEquals(new int[0], DataDrivenStationAdapter.sideOnlySlots(
                new int[]{0, 1}, new int[]{0, 1}, new int[]{0, 1}),
                "a mincer exposes its permissive input/output slots on every face");
        assertArrayEquals(new int[]{4}, DataDrivenStationAdapter.sideOnlySlots(
                new int[]{4}, new int[]{1, 2, 3}, new int[]{0}),
                "the Farm & Charm stove exposes its real fuel slot only horizontally");
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

    private java.io.InputStream blockTag(String path) {
        var stream = getClass().getResourceAsStream(path);
        return stream != null ? stream : getClass().getResourceAsStream(
                path.replace("/tags/block/", "/tags/blocks/"));
    }
}
