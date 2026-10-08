package com.aetherianartificer.townstead.work.station;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StationTargetLayoutTest {
    @Test
    void workstationParsesNamedTargetsAndAttendanceObservationRole() {
        WorkstationV2Def def = WorkstationV2Def.parse(id("test:bar"),
                JsonParser.parseString("""
                    {"blocks":["test:bar"],
                     "targets":{"orientation_property":"facing","roles":{
                       "tap":{"offset":[1,0,0],"blocks":["#test:taps"],"required":true},
                       "shelf":{"offset":[0,1,-1],"blocks":[],"required":false}}},
                     "behavior":{"type":"pheno:use_block","on":"tap","item":"empty"},
                     "attendance":{"incidents":[{
                       "id":"tap_ready","target":"tap",
                       "when":{"type":"pheno:constant","value":true},
                       "response":{"type":"pheno:use_block","on":"tap","item":"empty"}}]}}
                    """).getAsJsonObject());

        assertNotNull(def);
        assertNotNull(def.targetLayout());
        assertEquals(List.of("#test:taps"), def.targetLayout().targets().get("tap").blocks());
        assertEquals("tap", def.attendance().incidents().get(0).target());
    }

    @Test
    void rejectsUnboundedMalformedOrCollidingLayoutsAtTheRightBoundary() {
        assertNull(StationTargetLayout.parse(JsonParser.parseString("""
                {"roles":{"tap":{"offset":[9,0,0]}}}
                """)));
        assertNull(StationTargetLayout.parse(JsonParser.parseString("""
                {"roles":{"Tap":{"offset":[0,0,0]}}}
                """)));

        var layout = StationTargetLayout.parse(JsonParser.parseString("""
                {"roles":{"left":{"offset":[0,0,0],"blocks":[],"required":false},
                           "right":{"offset":[0,0,0],"blocks":[],"required":false}}}
                """));
        assertNotNull(layout, "collision is a runtime ownership diagnostic, not invalid syntax");
    }

    @Test
    void primitiveBlockSelectorParsesAsAnExplicitContextRole() {
        var selector = com.aetherianartificer.townstead.pheno.selector.BlockSelectors.parse(
                JsonParser.parseString("\"tap\""));
        assertNotNull(selector);
        assertNull(com.aetherianartificer.townstead.pheno.selector.BlockSelectors.parse(
                JsonParser.parseString("\"\"")));
    }

    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
