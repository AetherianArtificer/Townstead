package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.ApoliActionTranslator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The porter maps the raycast and effect-cloud families onto the native pheno:ray/beam/at/cloud. */
class RaycastCloudPortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void raycastBetweenBecomesABeamTowardTheTarget() {
        JsonElement out = ApoliActionTranslator.translateBiEntity(
                obj("{ 'type':'apugli:raycast_between', 'particle':'minecraft:crit', 'spacing':0.25 }"));
        assertTrue(out != null && out.isJsonObject());
        JsonObject beam = out.getAsJsonObject();
        assertEquals("pheno:beam", beam.get("type").getAsString());
        assertEquals("minecraft:crit", beam.get("particle").getAsString());
        assertEquals("target", beam.get("toward").getAsString());
    }

    @Test
    void explosionRaycastExplodesAtTheRayBlockHit() {
        JsonElement out = ApoliActionTranslator.translate(
                obj("{ 'type':'apugli:explosion_raycast', 'power':3.0, 'create_fire':true, 'distance':10 }"));
        assertTrue(out != null && out.isJsonObject(), "no particle -> a single at action");
        JsonObject at = out.getAsJsonObject();
        assertEquals("pheno:at", at.get("type").getAsString());
        assertEquals("pheno:ray", at.getAsJsonObject("blocks").get("type").getAsString());
        assertEquals("block", at.getAsJsonObject("blocks").get("stop_on").getAsString());
        JsonObject explode = at.getAsJsonObject("do");
        assertEquals("pheno:explode", explode.get("type").getAsString());
        assertEquals(3.0f, explode.get("power").getAsFloat());
        assertTrue(explode.get("fire").getAsBoolean());
    }

    @Test
    void customEffectCloudBecomesACloudWithAnInvertedOwnerAction() {
        JsonElement out = ApoliActionTranslator.translate(obj(
                "{ 'type':'apugli:spawn_custom_effect_cloud', 'radius':4.0, 'duration':200, "
                + "'particle':'minecraft:poison', 'owner_target_bientity_action':"
                + "{ 'type':'apoli:target_action', 'action':{ 'type':'apoli:damage', 'amount':2.0 } } }"));
        assertTrue(out != null && out.isJsonObject());
        JsonObject cloud = out.getAsJsonObject();
        assertEquals("pheno:cloud", cloud.get("type").getAsString());
        assertEquals(4.0, cloud.get("radius").getAsDouble());
        assertEquals("minecraft:poison", cloud.get("particle").getAsString());
        // do is wrapped in invert so the owner->inside action keeps its roles inside the cloud.
        assertEquals("pheno:invert", cloud.getAsJsonObject("do").get("type").getAsString());
    }
}
