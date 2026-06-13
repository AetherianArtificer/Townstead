package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.ApoliActionTranslator;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batch 2 entity-action ports onto native actions and existing composition. */
class EntityActionPortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void emitGameEventCarriesTheEvent() {
        JsonElement out = ApoliActionTranslator.translate(obj("{ 'type':'apoli:emit_game_event', 'event':'minecraft:eat' }"));
        assertTrue(out != null && out.isJsonObject());
        assertEquals("pheno:emit_game_event", out.getAsJsonObject().get("type").getAsString());
        assertEquals("minecraft:eat", out.getAsJsonObject().get("event").getAsString());
    }

    @Test
    void zombifyVillagerIsFieldless() {
        JsonElement out = ApoliActionTranslator.translate(obj("{ 'type':'apugli:zombify_villager' }"));
        assertEquals("pheno:zombify_villager", out.getAsJsonObject().get("type").getAsString());
    }

    @Test
    void setResourceBecomesChangeResourceSet() {
        JsonElement out = ApoliActionTranslator.translate(
                obj("{ 'type':'apoli:set_resource', 'resource':'pack:blood', 'value':5 }"));
        JsonObject o = out.getAsJsonObject();
        assertEquals("pheno:change_resource", o.get("type").getAsString());
        assertEquals("pack:blood", o.get("resource").getAsString());
        assertEquals(5, o.get("amount").getAsInt());
        assertEquals("set", o.get("operation").getAsString());
    }

    @Test
    void selectorActionRunsTheActionOnACommandSelection() {
        JsonElement out = ApoliActionTranslator.translate(obj(
                "{ 'type':'apoli:selector_action', 'selector':'@e[type=zombie]', "
                + "'bientity_action':{ 'type':'apoli:damage', 'amount':2.0 } }"));
        assertNotNull(out);
        JsonObject o = out.getAsJsonObject();
        JsonObject on = o.getAsJsonObject("on");
        assertEquals("pheno:command", on.get("type").getAsString());
        assertEquals("@e[type=zombie]", on.get("selector").getAsString());
    }
}
