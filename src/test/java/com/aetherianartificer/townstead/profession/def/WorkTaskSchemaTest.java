package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Professions declare villager AI work behaviors in data: the {@code work_tasks} array names
 * registered task types, gates them to workstation blocks or tags, and orders them by weight.
 * Unknown types and broken entries drop with diagnostics rather than silently idling villagers.
 */
class WorkTaskSchemaTest {

    @Test
    void cookDeclaresItsWorkTasks() {
        ProfessionDef cook = load("/data/townstead/profession/cook/profession.json", "townstead:cook");
        assertEquals(5, cook.workTasks().size());

        WorkTaskDef cookTask = cook.workTasks().get(0);
        assertEquals(id("townstead_work:cook"), cookTask.type());
        assertFalse(cookTask.anyWorkstation(), "stations are declared explicitly");
        assertTrue(cookTask.allowsBlock(id("farmersdelight:cooking_pot")));
        assertTrue(cookTask.recipes().isEmpty() && cookTask.recipesDenied().isEmpty(),
                "the village cook may produce every recipe");

        WorkTaskDef chopTask = cook.workTasks().get(1);
        assertEquals(id("townstead_work:chop"), chopTask.type());
        assertTrue(chopTask.allowsBlock(id("farmersdelight:cutting_board")));
        assertTrue(chopTask.allowsBlock(id("kaleidoscope_cookery:chopping_board")));
        assertFalse(chopTask.allowsBlock(id("farmersdelight:stove")),
                "chop admits only its declared boards");
        assertEquals(cookTask.weight(), chopTask.weight(),
                "equal weights merge into one selection pool, preserving engine ranking");

        // The furnace family is scoped rather than open: a cook smelts food, plus the few
        // non-food things a kitchen plausibly wants, and never iron ore.
        WorkTaskDef furnaceTask = cook.workTasks().get(2);
        assertEquals(id("townstead_work:cook"), furnaceTask.type());
        assertTrue(furnaceTask.allowsBlock(id("minecraft:smoker")));
        assertTrue(furnaceTask.allowsBlock(id("minecraft:blast_furnace")));
        assertFalse(furnaceTask.allowsBlock(id("farmersdelight:cooking_pot")),
                "the furnace task admits only furnace-family blocks");
        assertFalse(furnaceTask.recipes().edible() && furnaceTask.recipes().isEmpty(),
                "the edible token must survive alongside the exception tag");
        assertTrue(furnaceTask.recipes().edible(), "food outputs are admitted by kind, not by tag");
        assertFalse(furnaceTask.recipes().isEmpty(),
                "an open recipe set here would let a cook smelt ore");
        assertTrue(furnaceTask.weight() < cookTask.weight(),
                "furnaces are a fallback: real cookware is tried first");
        assertEquals(WorkTaskDef.Scope.VILLAGE, furnaceTask.scope(),
                "village furnaces are rarely inside a recognised kitchen");
        assertEquals(WorkTaskDef.Scope.WORKSITE, cookTask.scope(),
                "cookware stays in the kitchen it belongs to");

        WorkTaskDef farmAndCharmTask = cook.workTasks().get(3);
        assertTrue(farmAndCharmTask.allowsBlock(id("farm_and_charm:crafting_bowl")),
                "a recognized V2 station still needs profession task ownership");
        assertTrue(farmAndCharmTask.allowsBlock(id("farm_and_charm:mincer")));

        WorkTaskDef potTask = cook.workTasks().get(4);
        assertTrue(potTask.allowsBlock(id("caupona:stew_pot")),
                "Caupona's pots need a task of their own or the recipe gate refuses them");
        assertFalse(potTask.allowsBlock(id("minecraft:furnace")));
    }

    @Test
    void scopeDefaultsToWorksite() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "cook", "workstations": ["farmersdelight:cooking_pot"]}]}""",
                new Diagnostics());
        assertEquals(WorkTaskDef.Scope.WORKSITE, def.workTasks().get(0).scope(),
                "omitting scope must not widen where a villager works");
    }

    @Test
    void unreadableScopeDropsTheEntry() {
        Diagnostics diagnostics = new Diagnostics();
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "cook", "scope": "everywhere"}]}""", diagnostics);
        assertTrue(def.workTasks().isEmpty(),
                "a scope nobody can read must not fall back to a guess about where to work");
    }

    @Test
    void widestScopeWinsWhenTasksShareABucket() {
        assertEquals(WorkTaskDef.Scope.VILLAGE,
                WorkTaskDef.Scope.WORKSITE.widest(WorkTaskDef.Scope.VILLAGE));
        assertEquals(WorkTaskDef.Scope.VILLAGE,
                WorkTaskDef.Scope.VILLAGE.widest(WorkTaskDef.Scope.NEARBY));
        assertEquals(WorkTaskDef.Scope.NEARBY,
                WorkTaskDef.Scope.NEARBY.widest(WorkTaskDef.Scope.WORKSITE));
    }

    @Test
    void bareTypeResolvesToWorkNamespace() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "chop", "workstations": ["farmersdelight:cutting_board"]}]}""",
                new Diagnostics());
        assertEquals(1, def.workTasks().size());
        assertEquals(id("townstead_work:chop"), def.workTasks().get(0).type());
        assertEquals(1, def.workTasks().get(0).weight(), "weight defaults to 1");
    }

    @Test
    void omittedWorkstationsMeanTypeDefaults() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:brew"}]}""", new Diagnostics());
        WorkTaskDef brew = def.workTasks().get(0);
        assertTrue(brew.anyWorkstation());
        assertTrue(brew.allowsBlock(id("farmersdelight:cooking_pot")));
    }

    @Test
    void entitySetsNarrowTargets() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:slaughter",
                                 "entities": ["minecraft:chicken", "minecraft:rabbit"]}]}""",
                new Diagnostics());
        WorkTaskDef slaughter = def.workTasks().get(0);
        assertFalse(slaughter.anyEntity());
        assertTrue(slaughter.allowsEntityId(id("minecraft:chicken")));
        assertFalse(slaughter.allowsEntityId(id("minecraft:cow")),
                "a declared set admits only its members");
        assertTrue(slaughter.anyWorkstation(), "the two target axes are independent");
    }

    @Test
    void omittedEntitiesMeanEngineDefaults() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:slaughter"}]}""", new Diagnostics());
        assertTrue(def.workTasks().get(0).anyEntity());
        assertTrue(def.workTasks().get(0).allowsEntityId(id("minecraft:cow")));
    }

    @Test
    void allShippedDeclarationsUseRegisteredTypes() {
        for (String prof : new String[]{"/data/minecraft/profession/farmer/profession.json",
                "/data/minecraft/profession/butcher/profession.json",
                "/data/minecraft/profession/shepherd/profession.json",
                "/data/minecraft/profession/fisherman/profession.json",
                "/data/minecraft/profession/leatherworker/profession.json"}) {
            ProfessionDef def = load(prof, "minecraft:" + prof.split("/")[4]);
            assertFalse(def.workTasks().isEmpty(), prof + " declares its work tasks");
            for (WorkTaskDef task : def.workTasks()) {
                assertTrue(WorkTaskTypes.knows(task.type()),
                        prof + " declares unknown type " + task.type());
            }
        }
    }

    @Test
    void recipeSetsScopeProduction() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:cook",
                                 "recipes": ["farmersdelight:beef_stew", "minecraft:bread"],
                                 "deny_recipes": ["minecraft:bread"]}]}""",
                new Diagnostics());
        WorkTaskDef cook = def.workTasks().get(0);
        assertTrue(cook.allowsRecipe(id("farmersdelight:beef_stew"), id("farmersdelight:beef_stew")),
                "allow matches the recipe id");
        assertTrue(cook.allowsRecipe(id("farmersdelight:stew_from_leftovers"), id("farmersdelight:beef_stew")),
                "allow matches the output item id too");
        assertFalse(cook.allowsRecipe(id("minecraft:baked_potato"), id("minecraft:baked_potato")),
                "a non-empty allow set excludes everything else");
        assertFalse(cook.allowsRecipe(id("minecraft:bread"), id("minecraft:bread")),
                "deny wins over allow");
    }

    @Test
    void omittedRecipesMeanEverything() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:cook",
                                 "deny_recipes": ["minecraft:rotten_flesh"]}]}""",
                new Diagnostics());
        WorkTaskDef cook = def.workTasks().get(0);
        assertTrue(cook.allowsRecipe(id("farmersdelight:beef_stew"), id("farmersdelight:beef_stew")));
        assertFalse(cook.allowsRecipe(id("minecraft:rotten_flesh"), id("minecraft:rotten_flesh")),
                "deny works without an allow list");
    }

    @Test
    void recipeInputSetsScopeSharedStationRecipes() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:smoke",
                                  "recipe_inputs": ["minecraft:beef"],
                                  "deny_recipe_inputs": ["minecraft:poisonous_potato"]}]}""",
                new Diagnostics());
        WorkTaskDef smoke = def.workTasks().get(0);
        var beef = java.util.List.of(new com.aetherianartificer.townstead.work.recipe.RecipeIngredient(
                java.util.List.of(id("minecraft:beef")), 1));
        var beans = java.util.List.of(new com.aetherianartificer.townstead.work.recipe.RecipeIngredient(
                java.util.List.of(id("farmersdelight:coffee_beans")), 1));
        var denied = java.util.List.of(new com.aetherianartificer.townstead.work.recipe.RecipeIngredient(
                java.util.List.of(id("minecraft:poisonous_potato")), 1));

        assertTrue(smoke.allowsRecipe(id("minecraft:cooked_beef"), id("minecraft:cooked_beef"), beef));
        assertFalse(smoke.allowsRecipe(id("farmersdelight:roasted_coffee_beans"),
                id("farmersdelight:roasted_coffee_beans"), beans),
                "station capability must not grant recipe ownership");
        assertFalse(smoke.allowsRecipe(id("test:poison"), id("test:poison"), denied),
                "input denies win just like output denies");
    }

    @Test
    void shippedButcherScopesSmokingByWorkstationAndRawInputTag() {
        ProfessionDef butcher = load("/data/minecraft/profession/butcher/profession.json", "minecraft:butcher");
        assertTrue(butcher.jobSites().stream().anyMatch(site ->
                        site instanceof JobSiteProvider.Building building
                                && building.typePrefixes().contains("compat/butchery/butcher_shop_l")),
                "the generic producer needs an assigned building; an indoor smoker is not a standalone post");
        WorkTaskDef smoke = butcher.workTasks().stream()
                .filter(task -> task.type().equals(WorkTaskTypes.SMOKE))
                .findFirst().orElseThrow();
        assertTrue(smoke.workstations().tags().contains(id("townstead:smoker_stations")));
        assertTrue(smoke.recipeInputs().tags().contains(id("townstead:butcher_smoker_input")));
        assertEquals("townstead:butchered", smoke.historyCounter());
    }

    @Test
    void unknownTypeDropsWithDiagnostic() {
        Diagnostics diagnostics = new Diagnostics();
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:juggle"}]}""", diagnostics);
        assertTrue(def.workTasks().isEmpty());
        assertTrue(diagnostics.all().stream().anyMatch(d -> d.severity() == Severity.ERROR),
                "unknown work task types must surface as errors");
    }

    @Test
    void brokenRequirementsDropTheEntry() {
        // An unparseable gate must never read as always-on.
        Diagnostics diagnostics = new Diagnostics();
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:cook",
                                 "requirements": {"type": "townstead:not_a_condition"}}]}""",
                diagnostics);
        assertTrue(def.workTasks().isEmpty());
        assertTrue(diagnostics.all().stream().anyMatch(d -> d.severity() == Severity.ERROR));
    }

    private static ProfessionDef parse(String json, Diagnostics diagnostics) {
        diagnostics.forResource(id("test:subject"));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id("test:subject"), JsonParser.parseString(json).getAsJsonObject(),
                Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(def);
        return def;
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static ProfessionDef load(String resource, String idRaw) {
        InputStream in = WorkTaskSchemaTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        JsonObject json = JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id(idRaw));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id(idRaw), json, Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(def, resource + " must parse");
        return def;
    }
}
