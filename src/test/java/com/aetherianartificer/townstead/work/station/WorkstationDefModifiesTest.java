package com.aetherianartificer.townstead.work.station;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkstationDefModifiesTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    private static WorkstationDef parse(String json) {
        return WorkstationDef.parse(id("test:subject"), JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void produceLineCarriesWhatItModifies() {
        WorkstationDef def = parse("""
                { "schema": "townstead:workstation/v1", "type": "craft_surface",
                  "work_task": "townstead_work:craft", "blocks": ["lso:sewing_table"],
                  "produces": [
                    { "inputs": ["lso:heating_coat_1"], "output": "lso:heating_coat_1", "modifies": "armor", "time": 100 },
                    { "inputs": ["minecraft:paper", "minecraft:filled_map"], "output": "minecraft:filled_map", "copies": "minecraft:filled_map" }
                  ] }
                """);
        assertNotNull(def);
        assertEquals(2, def.produces().size());
        assertEquals("armor", def.produces().get(0).modifies());
        assertNull(def.produces().get(0).copies());
        assertEquals(100, def.produces().get(0).timeTicks());
        assertNull(def.produces().get(1).modifies());
        assertEquals(id("minecraft:filled_map"), def.produces().get(1).copies());
    }

    @Test
    void aLineCannotBothCopyAndModify() {
        assertNull(parse("""
                { "schema": "townstead:workstation/v1", "type": "craft_surface",
                  "work_task": "townstead_work:craft", "blocks": ["lso:sewing_table"],
                  "produces": [ { "inputs": ["a:b"], "output": "a:b", "copies": "a:b", "modifies": "armor" } ] }
                """));
        assertNull(parse("""
                { "schema": "townstead:workstation/v1", "type": "craft_surface",
                  "work_task": "townstead_work:craft", "blocks": ["lso:sewing_table"],
                  "produces": [ { "inputs": ["a:b"], "output": "a:b", "modifies": "" } ] }
                """));
    }
}
