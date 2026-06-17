package com.aetherianartificer.townstead.reaction;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReactionV2Test {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void parsesV2UnitsChoicesAndAnimationEnvelope() {
        Reaction reaction = Reaction.parse(
                ResourceLocation.tryParse("example:wave"),
                obj("{'schema':'townstead:reaction/v2','cooldown':'10s','chance':'75%',"
                        + "'choices':[{'animation':{'type':'emotecraft','id':'waving',"
                        + "'allow_movement':true,'speed':1.25},'cooldown':'2s','chance':'50%'}]}"));

        assertEquals(200, reaction.cooldownTicks());
        assertEquals(0.75F, reaction.chance());
        assertEquals(1, reaction.bindings().size());
        ReactionBinding binding = reaction.bindings().get(0);
        assertEquals("emotecraft", binding.backendKey());
        assertEquals("waving", binding.refIds().get(0));
        assertEquals(true, binding.allowMovement());
        assertEquals(40, binding.cooldownTicks());
        assertEquals(0.5F, binding.chance());
        assertEquals(1.25F, binding.args().orElseThrow().get("speed").getAsFloat());
    }

    @Test
    void rejectsUnknownSchema() {
        assertThrows(IllegalArgumentException.class, () -> Reaction.parse(
                ResourceLocation.tryParse("example:bad"),
                obj("{'schema':'townstead:reaction/v99'}")));
    }
}
