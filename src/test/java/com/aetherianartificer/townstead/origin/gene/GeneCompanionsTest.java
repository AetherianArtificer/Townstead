package com.aetherianartificer.townstead.origin.gene;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneCompanionsTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    private static final ResourceLocation FILE = DataPackLang.parseId("townstead_origins:triple_jump");
    private static final ResourceLocation DERIVED = DataPackLang.parseId("townstead_origins:triple_jump/jumps");

    @Test
    void peelsResourcesAndRewritesLocalRefs() {
        JsonObject root = obj("{ 'type':'pheno:trigger', 'trigger':'press', 'key':'jump',"
                + " 'resources':{ 'jumps':{ 'min':0, 'max':3, 'start':0 } },"
                + " 'action':{ 'type':'pheno:change_resource', 'resource':'jumps', 'by':1 } }");

        Map<ResourceLocation, JsonObject> companions = GeneCompanions.extract(FILE, root);

        // The companion is keyed by the derived id and carries the resource type.
        assertTrue(companions.containsKey(DERIVED));
        assertEquals("pheno:resource", companions.get(DERIVED).get("type").getAsString());
        assertEquals(3, companions.get(DERIVED).get("max").getAsInt());

        // The block is gone from the parent and the local ref now points at the derived id.
        assertFalse(root.has("resources"));
        assertEquals(DERIVED.toString(),
                root.getAsJsonObject("action").get("resource").getAsString());
    }

    @Test
    void leavesNamespacedRefsAndUndeclaredNamesAlone() {
        JsonObject root = obj("{ 'type':'pheno:trigger',"
                + " 'resources':{ 'jumps':{ 'max':3 } },"
                + " 'action':{ 'type':'pheno:and', 'actions':["
                + " { 'type':'pheno:change_resource', 'resource':'minecraft:mana', 'by':1 },"
                + " { 'type':'pheno:change_resource', 'resource':'other', 'by':1 } ] } }");

        GeneCompanions.extract(FILE, root);

        var actions = root.getAsJsonObject("action").getAsJsonArray("actions");
        assertEquals("minecraft:mana", actions.get(0).getAsJsonObject().get("resource").getAsString());
        assertEquals("other", actions.get(1).getAsJsonObject().get("resource").getAsString());
    }

    @Test
    void noResourcesBlockIsANoop() {
        JsonObject root = obj("{ 'type':'pheno:active_ability', 'cooldown':40 }");
        Map<ResourceLocation, JsonObject> companions = GeneCompanions.extract(FILE, root);
        assertTrue(companions.isEmpty());
        assertTrue(root.has("cooldown"));
    }
}
