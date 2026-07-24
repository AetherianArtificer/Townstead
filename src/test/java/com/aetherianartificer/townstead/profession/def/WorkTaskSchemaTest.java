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
        assertEquals(2, cook.workTasks().size());

        WorkTaskDef cookTask = cook.workTasks().get(0);
        assertEquals(id("townstead_work:cook"), cookTask.type());
        assertFalse(cookTask.anyWorkstation(), "stations are declared explicitly");
        assertTrue(cookTask.allowsBlock(id("farmersdelight:cooking_pot")));
        assertTrue(cookTask.recipes().isEmpty() && cookTask.recipesDenied().isEmpty(),
                "the village cook may produce every recipe");

        WorkTaskDef chopTask = cook.workTasks().get(1);
        assertEquals(id("townstead_work:chop"), chopTask.type());
        assertTrue(chopTask.allowsBlock(id("farmersdelight:cutting_board")));
        assertFalse(chopTask.allowsBlock(id("farmersdelight:stove")),
                "chop admits only its declared boards");
        assertEquals(cookTask.weight(), chopTask.weight(),
                "equal weights merge into one selection pool, preserving engine ranking");
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
