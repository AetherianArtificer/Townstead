package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Profession work sidecars declare villager AI behavior: the authored {@code tasks} array names
 * registered task types, gates them to workstation blocks or tags, and orders them by weight.
 * Unknown types and broken entries drop with diagnostics rather than silently idling villagers.
 */
class WorkTaskSchemaTest {

    @BeforeAll
    static void loadJobTaskVocabularyBeforeProfessionFixtures() {
        // Production reloads work_job before profession data. Mirror that ordering for the
        // classpath fixtures rather than restoring Job-owned ids to WorkTaskTypes.
        for (String resource : new String[]{
                "/data/townstead/work_job/butchery_carcass.json",
                "/data/townstead/work_job/butchery_clean_blood.json",
                "/data/townstead/work_job/butchery_golem.json",
                "/data/townstead/work_job/butchery_heads.json",
                "/data/townstead/work_job/butchery_leatherworking_rewet.json",
                "/data/townstead/work_job/butchery_rewet_cloth.json",
                "/data/townstead/work_job/butchery_sausage_hook.json",
                "/data/townstead/work_job/butchery_skin_rack.json",
                "/data/townstead/work_job/butchery_slaughter.json"}) {
            WorkTaskTypes.register(id(resourceJson(resource).get("task").getAsString()));
        }
        // The isolated Beekeeper-style examples stand in for a custom pack whose Job loader
        // would have declared this task before its Profession document was parsed.
        WorkTaskTypes.register(id("townstead_work:interact"));
    }

    @Test
    void cookDeclaresItsWorkTasks() {
        ProfessionDef cook = load("/data/townstead/profession/cook/profession.json", "townstead:cook");
        assertEquals(6, cook.workTasks().size());

        WorkTaskDef cookTask = cook.workTasks().get(0);
        assertEquals(id("townstead_work:cook"), cookTask.type());
        assertFalse(cookTask.anyWorkstation(), "stations are declared explicitly");
        assertTrue(cookTask.allowsBlock(id("farmersdelight:cooking_pot")));
        assertTrue(cookTask.recipes().isEmpty() && cookTask.recipesDenied().isEmpty(),
                "the village cook may produce every recipe");

        WorkTaskDef millTask = cook.workTasks().get(1);
        assertTrue(millTask.allowsBlock(id("kaleidoscope_cookery:millstone")));
        assertTrue(millTask.weight() < cookTask.weight(),
                "a Mill order is serviced after the cook's primary cookware");

        WorkTaskDef chopTask = cook.workTasks().get(2);
        assertEquals(id("townstead_work:chop"), chopTask.type());
        assertTrue(chopTask.allowsBlock(id("farmersdelight:cutting_board")));
        assertTrue(chopTask.allowsBlock(id("kaleidoscope_cookery:chopping_board")));
        assertFalse(chopTask.allowsBlock(id("farmersdelight:stove")),
                "chop admits only its declared boards");
        assertEquals(cookTask.weight(), chopTask.weight(),
                "equal weights merge into one selection pool, preserving engine ranking");

        // The furnace family is scoped rather than open: a cook smelts food, plus the few
        // non-food things a kitchen plausibly wants, and never iron ore.
        WorkTaskDef furnaceTask = cook.workTasks().get(3);
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

        WorkTaskDef farmAndCharmTask = cook.workTasks().get(4);
        assertTrue(farmAndCharmTask.allowsBlock(id("farm_and_charm:crafting_bowl")),
                "a recognized V2 station still needs profession task ownership");
        assertTrue(farmAndCharmTask.allowsBlock(id("farm_and_charm:mincer")));

        WorkTaskDef potTask = cook.workTasks().get(5);
        assertTrue(potTask.allowsBlock(id("caupona:stew_pot")),
                "Caupona's pots need a task of their own or the recipe gate refuses them");
        assertFalse(potTask.allowsBlock(id("minecraft:furnace")));
    }

    @Test
    void bakerHasAVanillaBaselineAndKeepsMillingSeparateFromTheMincer() {
        JsonObject baker = resourceJson("/data/townstead/profession/baker/profession.json");
        JsonObject work = resourceJson("/data/townstead/profession/baker/work.json");
        var tasks = work.getAsJsonArray("tasks").asList().stream()
                .map(entry -> WorkTaskDef.parse(entry.getAsJsonObject()))
                .toList();
        assertTrue(tasks.stream().allMatch(java.util.Objects::nonNull),
                "every shipped Baker task must parse");

        assertFalse(baker.has("mods"), "the Baker must exist in a vanilla-only installation");
        assertTrue(work.getAsJsonArray("poi").asList().stream().anyMatch(entry -> {
                    JsonObject site = entry.getAsJsonObject();
                    return "townstead:building".equals(site.get("type").getAsString())
                            && "bakery".equals(site.get("type_prefix").getAsString());
                }),
                "the Baker works from a Bakery, not a loose crafting table or a Kitchen");

        JsonObject bakeryBuilding = resourceJson("/data/mca/building_types/bakery.json");
        assertTrue(bakeryBuilding.get("visible").getAsBoolean());
        assertEquals(1, bakeryBuilding.getAsJsonObject("blocks")
                .get("minecraft:crafting_table").getAsInt());
        assertEquals(1, bakeryBuilding.getAsJsonObject("blocks")
                .get("#townstead:bakery/ovens").getAsInt());
        JsonObject bakeryExtension = resourceJson(
                "/data/townstead/extended_buildings/bakery.json");
        assertTrue(bakeryExtension.getAsJsonArray("workers").asList().stream()
                .anyMatch(value -> "townstead:baker".equals(value.getAsString())));

        WorkTaskDef baking = tasks.stream()
                .filter(task -> task.type().equals(WorkTaskTypes.CRAFT))
                .findFirst().orElseThrow();
        assertTrue(baking.allowsBlock(id("minecraft:crafting_table")));
        assertTrue(baking.recipes().tags().contains(id("townstead:orders/baker_goods")),
                "the Baker may craft baked goods, not every crafting-table recipe");

        WorkTaskDef oven = tasks.stream()
                .filter(task -> task.allowsBlock(id("minecraft:furnace")))
                .findFirst().orElseThrow();
        assertTrue(oven.allowsBlock(id("minecraft:smoker")));
        assertTrue(oven.recipes().tags().contains(id("townstead:orders/baker_goods")),
                "the oven policy must admit newly tagged baking recipes without naming their ids");
        assertEquals(WorkTaskDef.Scope.WORKSITE, oven.scope(),
                "the Baker's oven work belongs to the Bakery, not any furnace in the village");
        assertFalse(oven.recipes().isEmpty(),
                "a vanilla oven must not turn the Baker into a general-purpose cook or smelter");

        //? if >=1.21 {
        JsonObject bakerGoods = resourceJson(
                "/data/townstead/tags/item/orders/baker_goods.json");
        //?} else {
        /*JsonObject bakerGoods = resourceJson(
                "/data/townstead/tags/items/orders/baker_goods.json");
        *///?}
        assertFalse(bakerGoods.getAsJsonArray("values").asList().stream()
                        .anyMatch(value -> value.isJsonPrimitive()
                                && "minecraft:baked_potato".equals(value.getAsString())),
                "being cooked in a furnace does not make a potato bakery work");
        assertTrue(bakerGoods.getAsJsonArray("values").asList().stream()
                        .filter(com.google.gson.JsonElement::isJsonPrimitive)
                        .allMatch(value -> value.getAsString().startsWith("minecraft:")),
                "the root policy should stay semantic; provider details belong in child tags");
        assertTrue(bakerGoods.getAsJsonArray("values").asList().stream()
                        .filter(com.google.gson.JsonElement::isJsonObject)
                        .map(com.google.gson.JsonElement::getAsJsonObject)
                        .anyMatch(value -> "#c:bread".equals(value.get("id").getAsString())),
                "common tags are the automatic expansion seam for shared stations");
        assertTrue(bakerGoods.getAsJsonArray("values").asList().stream()
                        .filter(com.google.gson.JsonElement::isJsonObject)
                        .map(com.google.gson.JsonElement::getAsJsonObject)
                        .anyMatch(value -> "#townstead:compat/baker_goods/farm_and_charm"
                                .equals(value.get("id").getAsString())),
                "Farm & Charm's untagged baking products belong in a data-only provider seam");
        assertTrue(bakerGoods.getAsJsonArray("values").asList().stream()
                        .filter(com.google.gson.JsonElement::isJsonObject)
                        .map(com.google.gson.JsonElement::getAsJsonObject)
                        .anyMatch(value -> "#townstead:compat/baker_goods/bakery"
                                .equals(value.get("id").getAsString())),
                "Bakery's provider supplement must compose into the Baker policy");

        JsonObject farmAndCharmGoods = resourceJson(
                "/data/townstead/tags/item/compat/baker_goods/farm_and_charm.json");
        assertTrue(farmAndCharmGoods.getAsJsonArray("values").asList().stream()
                        .filter(com.google.gson.JsonElement::isJsonObject)
                        .map(com.google.gson.JsonElement::getAsJsonObject)
                        .anyMatch(value -> "farm_and_charm:grandmothers_strawberry_cake"
                                .equals(value.get("id").getAsString())
                                && !value.get("required").getAsBoolean()),
                "Farm & Charm's cake is not in a useful upstream cake tag, so compat supplies it");
        JsonObject bakeryGoods = resourceJson(
                "/data/townstead/tags/item/compat/baker_goods/bakery.json");
        assertTrue(bakeryGoods.getAsJsonArray("values").asList().stream()
                        .filter(com.google.gson.JsonElement::isJsonObject)
                        .map(com.google.gson.JsonElement::getAsJsonObject)
                        .anyMatch(value -> "#bakery:jam".equals(value.get("id").getAsString())),
                "Bakery's own semantic tags should be reused before listing its gaps");
        java.util.Set<String> expectedBakeryJams = java.util.Set.of(
                "bakery:apple_jam",
                "bakery:chocolate_jam",
                "bakery:glowberry_jam",
                "bakery:strawberry_jam",
                "bakery:sweetberry_jam");
        java.util.Set<String> bakeryJamFallbacks = bakeryGoods.getAsJsonArray("values").asList().stream()
                .filter(com.google.gson.JsonElement::isJsonObject)
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .map(value -> value.get("id").getAsString())
                .filter(expectedBakeryJams::contains)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expectedBakeryJams, bakeryJamFallbacks,
                "the currently shipped pot jams must remain orderable even if an upstream tag is absent");

        //? if >=1.21 {
        JsonObject jamKind = resourceJson(
                "/data/townstead/tags/item/orders/jam.json");
        //?} else {
        /*JsonObject jamKind = resourceJson(
                "/data/townstead/tags/items/orders/jam.json");
        *///?}
        java.util.Set<String> jamTags = jamKind.getAsJsonArray("values").asList().stream()
                .filter(com.google.gson.JsonElement::isJsonObject)
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .map(value -> value.get("id").getAsString())
                .collect(java.util.stream.Collectors.toSet());
        assertTrue(jamTags.contains("#c:jams"),
                "Jam is a cross-mod kind and should expand through the common convention");
        assertTrue(jamTags.contains("#bakery:jam"),
                "Bakery's own singular tag remains a compatibility fallback");
        assertEquals("Jam", com.aetherianartificer.townstead.work.order.OrdersService
                        .categoryLabel(id("townstead:orders/jam")),
                "the kind should be presented as Jam, not a provider-specific group");

        WorkTaskDef milling = tasks.stream()
                .filter(task -> task.allowsBlock(id("kaleidoscope_cookery:millstone")))
                .findFirst().orElseThrow();

        assertFalse(milling.allowsBlock(id("farm_and_charm:mincer")),
                "the mincer is a kitchen processor, not a milling workstation");

        //? if >=1.21 {
        JsonObject millTag = resourceJson("/data/townstead/tags/block/mill/workstations.json");
        //?} else {
        /*JsonObject millTag = resourceJson("/data/townstead/tags/blocks/mill/workstations.json");
        *///?}
        assertTrue(millTag.getAsJsonArray("values").asList().stream()
                        .noneMatch(value -> value.getAsJsonObject().get("id").getAsString()
                                .equals("farm_and_charm:mincer")),
                "a mincer must not satisfy the Mill building's workstation requirement");
    }

    @Test
    void bakeryCompatBuildingsKeepTheirSubmittedTierAndWorkforceContracts() {
        JsonObject work = resourceJson("/data/townstead/profession/baker/work.json");
        var sites = work.getAsJsonArray("poi").asList().stream()
                .map(element -> JobSiteProviders.parse(element.getAsJsonObject()))
                .filter(java.util.Objects::nonNull)
                .toList();
        assertTrue(sites.stream().anyMatch(site ->
                        site instanceof JobSiteProvider.Building building
                                && building.slotsFor("compat/bakery/bread_stand_l1") == 1),
                "the Bread Stand seats one Baker");
        assertTrue(sites.stream().anyMatch(site ->
                        site instanceof JobSiteProvider.Building building
                                && building.slotsFor("compat/bakery/bake_sale_l2") == 2),
                "the Bake Sale seats two Bakers");
        assertTrue(sites.stream().anyMatch(site ->
                        site instanceof JobSiteProvider.Building building
                                && building.slotsFor("compat/bakery/bakery_l3") == 3),
                "the full Bakery seats three Bakers");

        JsonObject stand = resourceJson(
                "/townstead_compat/building_types/compat/bakery/bread_stand_l1.json")
                .getAsJsonObject("blocks");
        assertEquals(1, stand.get("bakery:tray").getAsInt());
        assertEquals(1, stand.get("#townstead:kitchen/storage").getAsInt());
        assertEquals(1, stand.get("#townstead:furnace_stations").getAsInt());
        assertEquals(1, stand.get("farm_and_charm:stove").getAsInt(),
                "the Bread Stand must expose Bakery's core stove recipes");
        assertEquals(1, stand.get("bakery:small_cooking_pot").getAsInt(),
                "the Bread Stand must be able to make the preserves used by Bakery recipes");

        JsonObject sale = resourceJson(
                "/townstead_compat/building_types/compat/bakery/bake_sale_l2.json")
                .getAsJsonObject("blocks");
        assertEquals(1, sale.get("#townstead:compat/bakery/jams").getAsInt(),
                "any Bakery jam satisfies the submitted Any Jam requirement");
        assertFalse(sale.has("bakery:jar"),
                "Any Jam is the jar state, not an extra empty jar requirement");
        assertEquals(2, sale.get("#townstead:kitchen/storage").getAsInt());
        assertEquals(2, sale.get("#townstead:furnace_stations").getAsInt());
        assertEquals(1, sale.get("farm_and_charm:stove").getAsInt(),
                "every Bakery tier needs the stove that owns its core baking recipes");
        assertEquals(1, sale.get("farm_and_charm:crafting_bowl").getAsInt(),
                "the Bake Sale must be able to prepare dough for its advanced recipes");
        assertEquals(1, sale.get("bakery:baker_station").getAsInt(),
                "the Bake Sale is where decorated cakes and cupcakes first unlock");
        assertEquals(1, sale.get("bakery:small_cooking_pot").getAsInt(),
                "the Bake Sale must make its own jam, chocolate spread, and yeast");

        JsonObject full = resourceJson(
                "/townstead_compat/building_types/compat/bakery/bakery_l3.json")
                .getAsJsonObject("blocks");
        assertEquals(1, full.get("bakery:iron_table").getAsInt());
        assertEquals(3, full.get("#townstead:compat/bakery/jams").getAsInt());
        assertEquals(2, full.get("bakery:chocolate_jam").getAsInt());
        assertFalse(full.has("bakery:jar"));
        assertEquals(2, full.get("bakery:baker_station").getAsInt());
        assertEquals(2, full.get("farm_and_charm:crafting_bowl").getAsInt());
        assertEquals(1, full.get("bakery:small_cooking_pot").getAsInt());

        var fruitJams = resourceJson("/data/townstead/tags/block/compat/bakery/jams.json")
                .getAsJsonArray("values").asList().stream()
                .map(value -> value.getAsJsonObject().get("id").getAsString())
                .toList();
        assertEquals(java.util.List.of(
                "bakery:strawberry_jam",
                "bakery:sweetberry_jam",
                "bakery:glowberry_jam",
                "bakery:apple_jam"), fruitJams,
                "Chocolate Spread is a distinct requirement, not a fruit-jam alternative");

        for (String type : new String[]{"bread_stand_l1", "bake_sale_l2", "bakery_l3"}) {
            JsonObject extension = resourceJson(
                    "/data/townstead/extended_buildings/compat/bakery/" + type + ".json");
            assertTrue(extension.getAsJsonArray("workers").asList().stream()
                            .anyMatch(value -> "townstead:baker".equals(value.getAsString())),
                    type + " explicitly accepts Bakers");
        }
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
    void blockInteractionDeclaresItsStation() {
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:interact",
                                  "workstations": ["minecraft:beehive"],
                                  "scope": "worksite",
                                  "weight": 10}]}""",
                new Diagnostics());
        WorkTaskDef harvest = def.workTasks().get(0);
        assertEquals(id("townstead_work:interact"), harvest.type());
        assertTrue(harvest.allowsBlock(id("minecraft:beehive")));
        assertFalse(harvest.allowsBlock(id("minecraft:bee_nest")),
                "a sample author can decide which hive blocks belong to this job");
        assertEquals(WorkTaskDef.Scope.WORKSITE, harvest.scope());
        assertEquals(10, harvest.weight());
    }

    @Test
    void authoredHistoryCounterIsRejected() {
        Diagnostics diagnostics = new Diagnostics();
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:interact",
                                  "history_counter": "townstead_example:honey_harvested"}]}""",
                diagnostics);
        assertTrue(def.workTasks().isEmpty());
        assertTrue(diagnostics.all().stream().anyMatch(d -> d.severity() == Severity.ERROR));
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
        assertTrue(WorkTaskTypes.activities(smoke.type()).contains("townstead_work:smoke"));

        WorkTaskDef slaughter = butcher.workTasks().stream()
                .filter(task -> task.type().equals(id("townstead_work:slaughter")))
                .findFirst().orElseThrow();
        assertNotNull(slaughter.order(), "order presentation belongs to the work declaration");
        assertEquals("Slaughter", slaughter.order().name());
        assertEquals(id("minecraft:iron_sword"), slaughter.order().icon());

        var orderable = butcher.workTasks().stream()
                .filter(task -> task.order() != null)
                .map(WorkTaskDef::type)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(
                        id("townstead_work:dismantle"),
                        id("townstead_work:clean"),
                        id("townstead_work:hammer"),
                        id("townstead_work:slaughter")), orderable,
                "the four optional Butchery jobs must remain available to the order sheet");

        for (String building : java.util.List.of("butcher_shop_l1", "butcher_shop_l2",
                "butcher_shop_l3", "slaughter_pen", "slaughterhouse", "smokehouse")) {
            JsonObject extension = resourceJson(
                    "/data/townstead/extended_buildings/compat/butchery/" + building + ".json");
            assertTrue(extension.getAsJsonArray("workers").asList().stream()
                            .anyMatch(value -> "minecraft:butcher".equals(value.getAsString())),
                    building + " must advertise the Butcher whose Jobs its order sheet offers");
        }

        WorkTaskDef carcasses = butcher.workTasks().stream()
                .filter(task -> task.type().equals(id("townstead_work:butcher")))
                .findFirst().orElseThrow();
        assertNull(carcasses.order(),
                "mandatory pipeline work is omitted from the player's order choices");
    }

    @Test
    void malformedOrderPresentationDropsTheTask() {
        Diagnostics diagnostics = new Diagnostics();
        ProfessionDef def = parse("""
                {"work_tasks": [{"type": "townstead_work:slaughter",
                                  "order": {"name": "Slaughter"}}]}""", diagnostics);
        assertTrue(def.workTasks().isEmpty());
        assertTrue(diagnostics.all().stream().anyMatch(d -> d.severity() == Severity.ERROR));
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
        JsonObject json = resourceJson(resource);
        if ("townstead:cook".equals(idRaw)) {
            ProfessionPathDocument.apply(json, "pizzaiolo", resourceJson(
                    "/data/townstead/profession/cook/path/pizzaiolo/path.json"));
        }
        String directory = resource.substring(0, resource.lastIndexOf('/') + 1);
        ProfessionWorkOverlay.apply(json, resourceJson(directory + "work.json"));
        Diagnostics diagnostics = new Diagnostics();
        diagnostics.forResource(id(idRaw));
        ProfessionDef def = ProfessionDataLoader.parseProfession(
                id(idRaw), json, Map.of(), diagnostics, new LinkedHashMap<>());
        assertNotNull(def, resource + " must parse: " + diagnostics.all());
        return def;
    }

    private static JsonObject resourceJson(String resource) {
        InputStream in = WorkTaskSchemaTest.class.getResourceAsStream(resource);
        assertNotNull(in, "shipped resource missing: " + resource);
        return JsonParser.parseReader(
                new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
    }
}
