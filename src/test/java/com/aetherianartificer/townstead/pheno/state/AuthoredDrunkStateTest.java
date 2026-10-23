package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.ChoiceActionType;
import com.aetherianartificer.townstead.pheno.action.types.PerformanceActionType;
import com.aetherianartificer.townstead.pheno.action.types.SpeakActionType;
import com.aetherianartificer.townstead.pheno.action.types.WanderActionType;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoredDrunkStateTest {
    @BeforeAll
    static void registerActions() {
        ActionTypes.register(new ChoiceActionType());
        ActionTypes.register(new PerformanceActionType());
        ActionTypes.register(new SpeakActionType());
        ActionTypes.register(new WanderActionType());
    }

    @Test
    void allThreeAuthoredTiersParseThroughTheRealStateEffectVocabulary() throws Exception {
        StateEffect tipsy = load("drunk_tipsy_social");
        StateEffect drunk = load("drunk_social");
        StateEffect wasted = load("drunk_wasted_safety");

        assertEquals("tipsy", tipsy.tier());
        assertEquals("drunk", drunk.tier());
        assertEquals("wasted", wasted.tier());
        assertEquals(id("townstead_state:drunk"), tipsy.state());
        assertNotNull(tipsy.onEnter());
        assertNotNull(drunk.onTierChange());
        assertNotNull(wasted.onExit());
        assertTrue(wasted.priority() > drunk.priority());
        assertTrue(drunk.priority() > tipsy.priority());
    }

    private static StateEffect load(String name) throws Exception {
        String path = "/data/townstead/state_effect/" + name + ".json";
        try (var stream = AuthoredDrunkStateTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return StateEffect.parse(id("townstead:" + name),
                        JsonParser.parseReader(reader).getAsJsonObject());
            }
        }
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
