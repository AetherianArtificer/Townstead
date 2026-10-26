package com.aetherianartificer.townstead.root;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RootStatesTest {
    @Test
    void parsesDeclaredStates() {
        var states = RootStates.parse(JsonParser.parseString("""
                {"states":{"townstead_state:vampire":false,"townstead_state:drunk":true}}
                """).getAsJsonObject());
        assertEquals(false, states.get(ResourceLocation.tryParse("townstead_state:vampire")));
        assertEquals(true, states.get(ResourceLocation.tryParse("townstead_state:drunk")));
    }

    @Test
    void undeclaredIsEmptyAndMalformedIsRejected() {
        assertTrue(RootStates.parse(JsonParser.parseString("{}").getAsJsonObject()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RootStates.parse(JsonParser.parseString("""
                {"states":{"townstead_state:vampire":"no"}}
                """).getAsJsonObject()));
    }
}
