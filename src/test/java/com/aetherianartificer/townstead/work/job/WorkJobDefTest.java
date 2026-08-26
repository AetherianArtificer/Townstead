package com.aetherianartificer.townstead.work.job;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkJobDefTest {
    @Test
    void entityDeliveryUsesSemanticSourceAndDestinationFields() {
        registerEntityPrimitives();
        WorkJobDef def = WorkJobDef.parse(id("test:delivery"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v3",
                  "task":"townstead_work:slaughter",
                  "type":"townstead:entity_delivery",
                  "source":{
                      "buildings":["example:pen*"],
                      "results":{"minecraft:cow":"example:carcass"},
                      "action":{"type":"pheno:nothing"}
                  },
                  "destination":{
                      "blocks":["example:rail"],
                      "placement":{"offset":[0,-1,0],"properties":{"stage":1},
                                   "copy_properties":["facing"]}
                  }
                }
                """).getAsJsonObject());

        assertNotNull(def);
        assertEquals(WorkJobDef.ENTITY_DELIVERY, def.type());
        assertNotNull(def.source());
        assertNotNull(def.destination());
        assertNull(def.target());
        assertEquals(id("example:carcass"), def.resultFor(id("minecraft:cow")));
        assertTrue(def.source().matchesBuilding("example:pen_l2"));
    }

    @Test
    void entityDeliveryRequiresSourceAndDestination() {
        assertNull(WorkJobDef.parse(id("test:bad"), JsonParser.parseString("""
                {"task":"townstead_work:slaughter","type":"townstead:entity_delivery",
                 "source":{"results":{"minecraft:cow":"example:carcass"}}}
                """).getAsJsonObject()));
    }

    @Test
    void bundledButcheryBlockJobsUseCurrentSchema() throws Exception {
        registerProcedurePrimitives();
        for (String name : List.of("butchery_carcass", "butchery_golem", "butchery_heads",
                "butchery_clean_blood", "butchery_rewet_cloth", "butchery_sausage_hook",
                "butchery_skin_rack", "butchery_leatherworking_rewet")) {
            String path = "/data/townstead/work_job/" + name + ".json";
            try (var stream = getClass().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var json = JsonParser.parseReader(new InputStreamReader(
                        stream, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(WorkJobDef.SCHEMA, json.get("schema").getAsString());
                WorkJobDef def = WorkJobDef.parse(id("townstead:" + name), json);
                assertNotNull(def, path);
                assertEquals(WorkJobDef.BLOCK_INTERACTION, def.type());
                assertFalse(def.target().interactions().isEmpty());
            }
        }
    }

    private static void registerProcedurePrimitives() {
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.BlockActionType() {
                    @Override public String key() { return "pheno:use_block"; }
                    @Override public com.aetherianartificer.townstead.pheno.action.block.BlockAction parse(
                            com.google.gson.JsonObject json) { return context -> {}; }
                });
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.GameTimeValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.BlockDataValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.ArithmeticValueType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.DamageItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.ConsumeItemActionType());
        com.aetherianartificer.townstead.pheno.action.item.ItemActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.item.types.ChangeDataItemActionType());
        for (com.aetherianartificer.townstead.pheno.action.block.BlockActionType type : List.of(
                new com.aetherianartificer.townstead.pheno.action.block.types.SetBlockBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.OffsetBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.ModifyBlockStateBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.DestroyBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.ChangeBlockDataBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.ItemActionBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.LootTableBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.ReturnItemBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.PlaySoundBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.SpawnParticlesBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.LevelEventBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.IfElseBlockActionType(),
                new com.aetherianartificer.townstead.pheno.action.block.types.NothingBlockActionType())) {
            com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(type);
        }
    }

    @Test
    void blockInteractionKeepsDomainFactsInData() {
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.BlockActionType() {
                    @Override public String key() { return "pheno:use_block"; }
                    @Override public com.aetherianartificer.townstead.pheno.action.block.BlockAction parse(
                            com.google.gson.JsonObject json) { return context -> {}; }
                });
        WorkJobDef def = WorkJobDef.parse(id("test:hive"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v3",
                  "task":"townstead_work:interact",
                  "type":"townstead:block_interaction",
                  "target":{
                    "block":"minecraft:beehive",
                    "condition":{"type":"pheno:block_state","property":"honey_level","value":"5"},
                    "xp":4,
                    "interactions":[{
                      "item":"minecraft:shears",
                      "output":"minecraft:honeycomb"
                    }]
                  }
                }
                """).getAsJsonObject());

        assertNotNull(def);
        assertEquals(WorkJobDef.BLOCK_INTERACTION, def.type());
        WorkJobDef.BlockTarget target = def.target();
        assertNotNull(target);
        assertNotNull(target.condition());
        assertEquals(id("minecraft:beehive"), target.blocks().iterator().next());
        assertTrue(target.blockTags().isEmpty());
        assertEquals("minecraft:shears", target.interactions().get(0).item());
        assertEquals(id("minecraft:honeycomb"),
                target.interactions().get(0).outputs().iterator().next());
        assertEquals(4, target.interactions().get(0).xp());
        assertEquals("test:hive", def.activityKey(),
                "the Job resource id is its automatic Chronicle activity");
    }

    @Test
    void currentSchemaAcceptsBlockTagsConditionsAndOutputlessActions() {
        WorkJobDef def = WorkJobDef.parse(id("test:cleanup"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v3",
                  "task":"townstead_work:clean",
                  "type":"townstead:block_interaction",
                  "target":{
                    "blocks":["#example:spills"],
                    "interactions":[{
                      "item":"#example:cloths",
                      "condition":{"type":"pheno:constant","value":true},
                      "action":{"type":"pheno:use_block"}
                    }]
                  }
                }
                """).getAsJsonObject());

        assertNotNull(def);
        assertEquals(List.of(id("example:spills")), def.target().blockTags());
        assertTrue(def.target().interactions().get(0).outputs().isEmpty());
        assertNotNull(def.target().interactions().get(0).condition());
    }

    @Test
    void managedRequirementsUseAnExplicitSatisfiedConditionAndProvision() {
        registerProcedurePrimitives();
        WorkJobDef def = WorkJobDef.parse(id("test:managed"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v3",
                  "task":"townstead_work:interact",
                  "type":"townstead:block_interaction",
                  "target":{
                    "block":"minecraft:beehive",
                    "requirements":[{
                      "id":"smoke",
                      "satisfied_when":{"type":"pheno:smokey"},
                      "provision":{
                        "at":{"offset":[0,-1,0]},
                        "item":"minecraft:flint_and_steel",
                        "start":{"type":"pheno:use_block","item":"item"},
                        "managed_when":{"type":"pheno:block_state","property":"lit","value":"true"},
                        "stop":{"type":"pheno:modify_block_state","property":"lit","value":"false"}
                      }
                    }],
                    "interactions":[{"item":"minecraft:shears","output":"minecraft:honeycomb"}]
                  }
                }
                """).getAsJsonObject());

        assertNotNull(def);
        assertEquals(1, def.target().requirements().size());
        WorkJobDef.ManagedRequirement requirement = def.target().requirements().get(0);
        assertEquals("smoke", requirement.id());
        assertNotNull(requirement.satisfiedWhen());
        assertNotNull(requirement.provision());
        assertEquals("minecraft:flint_and_steel", requirement.provision().item());
        assertNotNull(requirement.provision().managedWhen());
    }

    @Test
    void managedRequirementRejectsAmbiguousWhenField() {
        registerProcedurePrimitives();
        assertNull(WorkJobDef.parse(id("test:ambiguous"), JsonParser.parseString("""
                {
                  "task":"townstead_work:interact",
                  "type":"townstead:block_interaction",
                  "target":{
                    "block":"minecraft:beehive",
                    "requirements":[{"id":"smoke","when":{"type":"pheno:smokey"}}],
                    "interactions":[{"item":"minecraft:shears","output":"minecraft:honeycomb"}]
                  }
                }
                """).getAsJsonObject()));
    }

    @Test
    void bundledButcheryJobUsesSemanticFields() throws Exception {
        registerProcedurePrimitives();
        registerEntityPrimitives();
        String path = "/data/townstead/work_job/butchery_slaughter.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            WorkJobDef def = WorkJobDef.parse(id("townstead:butchery_slaughter"),
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject());
            assertNotNull(def);
            assertNotNull(def.source());
            assertEquals("#townstead:butcher_cleavers", def.source().item());
            assertNotNull(def.source().condition());
            assertNotNull(def.source().action());
            assertEquals(20, def.source().interval());
            assertEquals(2400, def.source().cooldown().fallback());
            assertNotNull(def.source().cooldown().config());
            assertEquals(2, def.source().xp());
            assertNotNull(def.destination());
            assertNotNull(def.destination().placement().action());
            assertTrue(def.destination().blocks()
                    .contains(id("butchery:hook")));
        }
    }

    private static void registerEntityPrimitives() {
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:and",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.AND));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:alive", ctx -> true));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:baby", ctx -> false));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:named", ctx -> false));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                        "pheno:tamed", ctx -> false));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.ConfigConditionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.NothingActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.SwingHandActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.TargetActionType());
        com.aetherianartificer.townstead.pheno.action.ActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.types.DamageActionType());
    }

    @Test
    void unpublishedFreeFormRolesShapeIsRejected() {
        assertNull(WorkJobDef.parse(id("test:old"), JsonParser.parseString("""
                {"task":"townstead_work:interact","executor":"townstead:block_interaction",
                 "roles":{"hive":{"kind":"block","blocks":["minecraft:beehive"]}}}
                """).getAsJsonObject()));
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
