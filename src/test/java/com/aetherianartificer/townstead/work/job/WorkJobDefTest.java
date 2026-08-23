package com.aetherianartificer.townstead.work.job;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WorkJobDefTest {
    @Test
    void entityDeliveryUsesSemanticSourceAndDestinationFields() {
        WorkJobDef def = WorkJobDef.parse(id("test:delivery"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v1",
                  "task":"townstead_work:slaughter",
                  "type":"townstead:entity_delivery",
                  "source":{
                      "buildings":["example:pen*"],
                      "results":{"minecraft:cow":"example:carcass"}
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
    void blockInteractionKeepsDomainFactsInData() {
        com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes.register(
                new com.aetherianartificer.townstead.pheno.action.block.BlockActionType() {
                    @Override public String key() { return "pheno:use_block"; }
                    @Override public com.aetherianartificer.townstead.pheno.action.block.BlockAction parse(
                            com.google.gson.JsonObject json) { return context -> {}; }
                });
        WorkJobDef def = WorkJobDef.parse(id("test:hive"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v1",
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
        assertEquals("minecraft:shears", target.interactions().get(0).item());
        assertEquals(id("minecraft:honeycomb"),
                target.interactions().get(0).outputs().iterator().next());
        assertEquals(4, target.interactions().get(0).xp());
        assertEquals("test:hive", def.activityKey(),
                "the Job resource id is its automatic Chronicle activity");
    }

    @Test
    void bundledButcheryJobUsesSemanticFields() throws Exception {
        String path = "/data/townstead/work_job/butchery_slaughter.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            WorkJobDef def = WorkJobDef.parse(id("townstead:butchery_slaughter"),
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject());
            assertNotNull(def);
            assertNotNull(def.source());
            assertNotNull(def.destination());
            assertTrue(def.destination().blocks()
                    .contains(id("butchery:hook")));
        }
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
