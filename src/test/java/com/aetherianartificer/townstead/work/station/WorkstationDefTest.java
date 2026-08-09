package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.StationType;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-declared workstations: blocks map onto station roles with pot-protocol layout and an
 * optional exclusive recipe type, so packs extend mod support without Java.
 */
class WorkstationDefTest {

    private static WorkstationDef parse(String json) {
        return WorkstationDef.parse(id("test:subject"), JsonParser.parseString(json).getAsJsonObject());
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    @Test
    void aliasFormMapsBlocksOntoARole() {
        WorkstationDef def = parse("""
                {"blocks": ["rusticdelight:cooking_pot", "#somepack:pots"], "type": "hot_station"}""");
        assertNotNull(def);
        assertEquals(StationType.HOT_STATION, def.role());
        assertTrue(def.blocks().contains(id("rusticdelight:cooking_pot")));
        assertTrue(def.blockTags().contains(id("somepack:pots")));
        assertEquals(7, def.containerSlot(), "pot layout defaults to Farmer's Delight's");
        assertEquals(6, def.ingredientSlots());
        assertNull(def.recipeType(), "no recipe type means the built-in recipe families");
    }

    @Test
    void fullFormDeclaresProtocolAndRecipes() {
        WorkstationDef def = parse("""
                {"block": "examplemod:boiling_pot", "type": "hot_station",
                 "container_slot": 9, "ingredient_slots": 4,
                 "recipe_type": "examplemod:boiling", "recipe_tier": 2,
                 "cook_time": 300, "beverage": false}""");
        assertNotNull(def);
        assertEquals(9, def.containerSlot());
        assertEquals(4, def.ingredientSlots());
        assertEquals(id("examplemod:boiling"), def.recipeType());
        assertEquals(2, def.recipeTier());
        assertEquals(300, def.cookTimeTicks());
    }

    @Test
    void passiveStationOwnsRecipesFromItsDeclaredType() {
        WorkstationDef stove = parse("""
                {"block": "farm_and_charm:stove", "type": "passive_station",
                 "recipe_type": "farm_and_charm:stove", "fuel": true}""");
        assertNotNull(stove);
        assertTrue(StationRecipeOwnership.ownsDeclaredType(
                stove, StationType.PASSIVE_STATION, id("farm_and_charm:stove")));
        assertFalse(StationRecipeOwnership.ownsDeclaredType(
                stove, StationType.PASSIVE_STATION, id("farm_and_charm:pot_cooking")));
        assertFalse(StationRecipeOwnership.ownsDeclaredType(
                stove, StationType.HOT_STATION, id("farm_and_charm:stove")));
        assertTrue(StationRecipeOwnership.isFuelRequirement(
                new com.aetherianartificer.townstead.work.recipe.RecipeIngredient(
                        java.util.List.of(com.aetherianartificer.townstead.supply.TownsteadSupplyLines.FURNACE_FUEL), 1)),
                "virtual fuel is handled through the fuel face, not an ingredient slot");
    }

    @Test
    void orderableSpeaksAtTheStationAltitude() {
        WorkstationDef def = parse("""
                {"block": "examplemod:oven", "type": "passive_station",
                 "recipe_type": "examplemod:baking", "orderable": "all"}""");
        assertNotNull(def);
        assertTrue(def.orderable().all(), "\"all\" admits the station's whole menu");

        def = parse("""
                {"block": "examplemod:grinder", "type": "passive_station",
                 "recipe_type": "examplemod:grinding",
                 "orderable": {"allow": ["examplemod:flour", "#c:breads"], "block": ["minecraft:diamond"]}}""");
        assertNotNull(def);
        assertFalse(def.orderable().all(), "an object form without a mode stays tag-gated");
        assertEquals(java.util.List.of("examplemod:flour", "#c:breads"), def.orderable().allow());
        assertEquals(java.util.List.of("minecraft:diamond"), def.orderable().block());

        def = parse("""
                {"block": "examplemod:oven", "type": "passive_station",
                 "recipe_type": "examplemod:baking"}""");
        assertNotNull(def);
        assertEquals(WorkstationDef.Orderable.TAGGED, def.orderable(), "absent means the trade tag decides");

        assertNull(parse("""
                {"block": "examplemod:oven", "type": "passive_station",
                 "recipe_type": "examplemod:baking", "orderable": "everything"}"""),
                "an unrecognised mode must refuse the def, not read as satisfied");
        assertNull(parse("""
                {"block": "examplemod:oven", "type": "passive_station",
                 "recipe_type": "examplemod:baking", "orderable": {"allow": ["not a valid id!"]}}"""),
                "a malformed id in a list must refuse the def");
    }

    @Test
    void supportBelowIsDeclaredAndOptional() {
        WorkstationDef def = parse("""
                {"block": "examplemod:pot", "type": "passive_station",
                 "recipe_type": "examplemod:boiling",
                 "support_below": ["#examplemod:heat", "minecraft:magma_block"]}""");
        assertNotNull(def);
        assertTrue(def.supportBelow().contains(id("minecraft:magma_block")));
        assertTrue(def.supportBelowTags().contains(id("examplemod:heat")));

        def = parse("""
                {"block": "examplemod:pot", "type": "passive_station",
                 "recipe_type": "examplemod:boiling", "support_below": "#examplemod:heat"}""");
        assertNotNull(def, "a lone entry needs no array");
        assertTrue(def.supportBelowTags().contains(id("examplemod:heat")));

        def = parse("""
                {"block": "examplemod:bench", "type": "passive_station",
                 "recipe_type": "examplemod:carving"}""");
        assertNotNull(def);
        assertTrue(def.supportBelow().isEmpty() && def.supportBelowTags().isEmpty(),
                "a station that needs nothing under it stands on its own");

        assertNull(parse("""
                {"block": "examplemod:pot", "type": "passive_station",
                 "recipe_type": "examplemod:boiling", "support_below": []}"""),
                "an empty requirement is a requirement nothing can satisfy, not 'no requirement'");
    }

    @Test
    void namingAFluidReaderIsItselfTheOutputDeclaration() {
        WorkstationDef def = parse("""
                {"block": "caupona:stew_pot", "type": "passive_station",
                 "fluid_source": "townstead:caupona", "adapter": "townstead:caupona_pot"}""");
        assertNotNull(def, "a fluid reader says where the outputs come from, so no produce lines");
        assertTrue(def.fluidStation());
        assertEquals("townstead:caupona", def.fluidSource());

        assertNull(parse("""
                {"block": "caupona:stew_pot", "type": "passive_station", "fluid_source": ""}"""),
                "an unnamed reader would silently read nothing");
        assertNull(parse("""
                {"block": "examplemod:barrel", "type": "passive_station"}"""),
                "a passive station still has to say what comes out of it");
    }

    @Test
    void shippedFarmersDelightDefsMirrorTheEngine() throws Exception {
        record Expected(String file, String block) {}
        for (Expected e : new Expected[]{
                new Expected("cooking_pot", "farmersdelight:cooking_pot"),
                new Expected("skillet", "farmersdelight:skillet"),
                new Expected("stove", "farmersdelight:stove"),
                new Expected("cutting_board", "farmersdelight:cutting_board")}) {
            WorkstationV2Def def = parseV2Resource("/data/townstead/workstation/" + e.file() + ".json");
            assertTrue(def.blocks().contains(id(e.block())), e.file());
        }
        WorkstationV2Def pot = parseV2Resource("/data/townstead/workstation/cooking_pot.json");
        assertEquals(java.util.List.of(7), pot.containerSlots());
        WorkstationDef campfire = parseResource("/data/townstead/workstation/campfire.json");
        assertEquals(StationType.FIRE_STATION, campfire.role());
        assertTrue(campfire.blockTags().contains(id("minecraft:campfires")));
    }

    private static WorkstationDef parseResource(String resource) throws Exception {
        try (var in = WorkstationDefTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "shipped resource missing: " + resource);
            WorkstationDef def = WorkstationDef.parse(id("townstead:test"),
                    JsonParser.parseReader(new java.io.InputStreamReader(
                            in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            assertNotNull(def, resource + " must parse");
            return def;
        }
    }

    private static WorkstationV2Def parseV2Resource(String resource) throws Exception {
        try (var in = WorkstationDefTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "shipped resource missing: " + resource);
            WorkstationV2Def def = WorkstationV2Def.parse(id("townstead:test"),
                    JsonParser.parseReader(new java.io.InputStreamReader(
                            in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject());
            assertNotNull(def, resource + " must parse as V2");
            return def;
        }
    }

    @Test
    void authoredStandsParseAsOffsets() throws Exception {
        WorkstationDef def = parse("""
                {"block": "examplemod:grill", "type": "fire_station",
                 "stands": [[1, -1, 0], [0, -1, 1]]}""");
        assertNotNull(def);
        assertEquals(2, def.stands().size());
        assertEquals(new net.minecraft.core.Vec3i(1, -1, 0), def.stands().get(0));
        assertNull(parse("""
                {"block": "examplemod:grill", "type": "fire_station",
                 "stands": [[1, 0]]}"""), "offsets must be three ints");

        WorkstationV2Def skillet = parseV2Resource("/data/townstead/workstation/skillet.json");
        assertTrue(skillet.containerSlots().isEmpty(), "pathfinding discovers a safe stand at runtime");
    }

    @Test
    void protocolRolesParseTheirLifecycle() {
        WorkstationDef def = parse("""
                {"type": "passive_station", "block": "examplemod:barrel",
                 "produces": [{"inputs": ["minecraft:milk_bucket", "#examplemod:cultures"],
                               "output": "examplemod:cheese", "time": 1200}]}""");
        assertNotNull(def);
        assertEquals(StationType.PASSIVE_STATION, def.role());
        assertEquals(1, def.produces().size());
        WorkstationDef.Produce produce = def.produces().get(0);
        assertEquals(2, produce.inputs().size());
        assertEquals(id("examplemod:cheese"), produce.output());
        assertEquals(1200, produce.timeTicks());
        assertNull(parse("""
                {"type": "passive_station", "block": "examplemod:barrel"}"""),
                "a protocol station without produces can do nothing and must refuse");
    }

    @Test
    void shippedPizzaDelightDefsDeclareTheProtocols() throws Exception {
        WorkstationDef basin = parseResource("/data/townstead/workstation/basin.json");
        assertEquals(StationType.PASSIVE_STATION, basin.role());
        assertEquals("townstead:pizzadelight_basin", basin.adapter());
        assertEquals(id("pizzadelight:cheese_block"), basin.produces().get(0).output());

        WorkstationDef oven = parseResource("/data/townstead/workstation/pizza_oven.json");
        assertEquals(StationType.PLACE_SURFACE, oven.role());
        assertEquals(id("pizzadelight:raw_pizza"), oven.places());
        assertEquals(id("pizzadelight:pizza"), oven.doneBlock());
        assertTrue(oven.surfaceTags().contains(id("farmersdelight:heat_sources")),
                "the bake anchors on Farmer's Delight heat sources");
        assertTrue(oven.harvestTools().contains(id("pizzadelight:iron_pizza_peel")),
                "harvest requires a peel, like a player");
        WorkstationDef.Produce pizza = oven.produces().get(0);
        assertEquals(id("pizzadelight:ingredients"), pizza.extrasTag());
        assertEquals(9, pizza.extrasMax(), "up to nine distinct toppings drive the taste tier");
    }

    @Test
    void malformedDefsRefuse() {
        assertNull(parse("""
                {"blocks": ["somemod:grill"]}"""), "role is required");
        assertNull(parse("""
                {"type": "hot_station"}"""), "at least one block is required");
        assertNull(parse("""
                {"blocks": ["somemod:grill"], "type": "grill"}"""), "unknown roles refuse");
    }

    @Test
    void furnaceStationNeedsARecipeType() {
        // Its outputs come from a recipe type, not from declared produce lines, so without one
        // the station could do nothing and is better refused than loaded inert.
        assertNull(parse("""
                {"type": "furnace_station", "blocks": ["minecraft:furnace"]}"""));
    }

    @Test
    void furnaceSlotsDefaultToVanillaAndAreOverridable() {
        WorkstationDef vanilla = parse("""
                {"type": "furnace_station", "blocks": ["minecraft:furnace"],
                 "recipe_type": "minecraft:smelting"}""");
        assertNotNull(vanilla);
        assertEquals(0, vanilla.furnaceSlots().input());
        assertEquals(1, vanilla.furnaceSlots().fuel());
        assertEquals(2, vanilla.furnaceSlots().output());

        WorkstationDef odd = parse("""
                {"type": "furnace_station", "blocks": ["othermod:kiln"],
                 "recipe_type": "minecraft:smelting",
                 "input_slot": 3, "fuel_slot": 4, "output_slot": 5}""");
        assertNotNull(odd);
        assertEquals(3, odd.furnaceSlots().input());
        assertEquals(4, odd.furnaceSlots().fuel());
        assertEquals(5, odd.furnaceSlots().output());
    }

    @Test
    void passiveStationMaySourceItsOutputFromARecipeType() {
        // A modded station with its own recipe type, driven through its item handler: the outputs
        // come from discovery, so demanding inline produce lines would reject it for no reason.
        WorkstationDef def = parse("""
                {"type": "passive_station", "blocks": ["farm_and_charm:cooking_pot"],
                 "recipe_type": "farm_and_charm:pot_cooking"}""");
        assertNotNull(def);
        assertEquals(id("farm_and_charm:pot_cooking"), def.recipeType());
        assertTrue(def.produces().isEmpty());
    }

    @Test
    void passiveStationStatingNoOutputAtAllIsRejected() {
        assertNull(parse("""
                {"type": "passive_station", "blocks": ["othermod:barrel"]}"""));
    }

    @Test
    void placeSurfaceStillNeedsInlineProduceLines() {
        // Its output is a block it becomes, which no recipe type can describe.
        assertNull(parse("""
                {"type": "place_surface", "blocks": ["othermod:dough"],
                 "recipe_type": "othermod:baking"}"""));
    }
}
