package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.ApoliActionTranslator;
import com.aetherianartificer.townstead.origin.port.ApoliBlockActionTranslator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Apoli block-action translator and its use in the raycast ports. */
class BlockActionPortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void explodeBlockActionMapsPowerAndDestruction() {
        JsonElement out = ApoliBlockActionTranslator.translate(
                obj("{ 'type':'apoli:explode', 'power':2.5, 'create_fire':true, 'destruction_type':'break' }"));
        assertTrue(out != null && out.isJsonObject());
        JsonObject explode = out.getAsJsonObject();
        assertEquals("pheno:explode", explode.get("type").getAsString());
        assertEquals(2.5f, explode.get("power").getAsFloat());
        assertTrue(explode.get("fire").getAsBoolean());
        assertTrue(explode.get("destroy").getAsBoolean(), "break is not keep -> destroy");
    }

    @Test
    void areaOfEffectRecursesIntoItsInnerBlockAction() {
        JsonElement out = ApoliBlockActionTranslator.translate(obj(
                "{ 'type':'apoli:area_of_effect', 'radius':3, 'shape':'sphere', "
                + "'block_action':{ 'type':'apugli:destroy', 'drop_block':false } }"));
        assertTrue(out != null && out.isJsonObject());
        JsonObject aoe = out.getAsJsonObject();
        assertEquals("pheno:area_of_effect", aoe.get("type").getAsString());
        assertEquals(3, aoe.get("radius").getAsInt());
        assertEquals("sphere", aoe.get("shape").getAsString());
        JsonObject inner = aoe.getAsJsonObject("block_action");
        assertEquals("pheno:destroy", inner.get("type").getAsString());
        assertEquals(false, inner.get("drop_item").getAsBoolean());
    }

    @Test
    void lightUpHasNoEquivalentAndIsSkipped() {
        assertNull(ApoliBlockActionTranslator.translate(obj("{ 'type':'apugli:light_up', 'light_campfire':true }")));
    }

    @Test
    void raycastBetweenNowCarriesItsBlockActionThroughAt() {
        JsonElement out = ApoliActionTranslator.translateBiEntity(obj(
                "{ 'type':'apugli:raycast_between', 'particle':'minecraft:crit', "
                + "'block_action':{ 'type':'apoli:bonemeal' } }"));
        assertTrue(out != null && out.isJsonArray(), "beam + at");
        boolean hasAt = false;
        for (JsonElement e : out.getAsJsonArray()) {
            JsonObject o = e.getAsJsonObject();
            if ("pheno:at".equals(o.get("type").getAsString())) {
                hasAt = true;
                assertEquals("pheno:ray", o.getAsJsonObject("blocks").get("type").getAsString());
                assertEquals("target", o.getAsJsonObject("blocks").get("toward").getAsString());
                assertEquals("pheno:bonemeal", o.getAsJsonObject("do").get("type").getAsString());
            }
        }
        assertTrue(hasAt, "the block action is run at the ray's block hit");
    }
}
