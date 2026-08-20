package com.aetherianartificer.townstead.work.job;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class WorkJobDefTest {
    @Test
    void entityDeliveryUsesRoleKindsRatherThanMagicRoleNames() {
        WorkJobDef def = WorkJobDef.parse(id("test:delivery"), JsonParser.parseString("""
                {
                  "schema":"townstead:job/v1",
                  "task":"townstead_work:slaughter",
                  "executor":"townstead:entity_delivery",
                  "roles":{
                    "whatever_the_author_calls_it":{
                      "kind":"entity",
                      "buildings":["example:pen*"],
                      "results":{"minecraft:cow":"example:carcass"}
                    },
                    "somewhere_to_put_it":{
                      "kind":"block",
                      "blocks":["example:rail"],
                      "placement":{"offset":[0,-1,0],"properties":{"stage":1},
                                   "copy_properties":["facing"]}
                    }
                  }
                }
                """).getAsJsonObject());

        assertNotNull(def);
        assertNotNull(def.first(WorkJobDef.RoleKind.ENTITY));
        assertNotNull(def.first(WorkJobDef.RoleKind.BLOCK));
        assertNull(def.roles().get("hook"));
        assertEquals(id("example:carcass"), def.resultFor(id("minecraft:cow")));
        assertTrue(def.first(WorkJobDef.RoleKind.ENTITY).matchesBuilding("example:pen_l2"));
    }

    @Test
    void entityDeliveryRequiresEntityAndBlockRoles() {
        assertNull(WorkJobDef.parse(id("test:bad"), JsonParser.parseString("""
                {"task":"townstead_work:slaughter","executor":"townstead:entity_delivery",
                 "roles":{"source":{"kind":"entity","results":{"minecraft:cow":"example:carcass"}}}}
                """).getAsJsonObject()));
    }

    @Test
    void bundledButcheryJobKeepsRoleNamesGeneric() throws Exception {
        String path = "/data/townstead/work_job/butchery_slaughter.json";
        try (var stream = getClass().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            WorkJobDef def = WorkJobDef.parse(id("townstead:butchery_slaughter"),
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                            .getAsJsonObject());
            assertNotNull(def);
            assertEquals(java.util.Set.of("source", "destination"), def.roles().keySet());
            assertFalse(def.roles().containsKey("hook"));
            assertTrue(def.first(WorkJobDef.RoleKind.BLOCK).blocks()
                    .contains(id("butchery:hook")));
        }
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
