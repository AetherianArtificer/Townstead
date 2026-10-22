package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentPhysicsConfigTest {

    private static final Map<String, JsonObject> PRESETS = Map.of(
            "townstead:tail", JsonParser.parseString("""
                    {"frequency":1.4,"damping_ratio":0.35,"gravity":0.25,"limit":45}
                    """).getAsJsonObject(),
            "examplepack:tail", JsonParser.parseString("""
                    {"frequency":2.0}
                    """).getAsJsonObject());

    @Test
    void bareChainTakesTheDefaults() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["tail"]}
                """);

        assertEquals(AttachmentDef.Knob.of(PhysicsChains.FREQUENCY), chain.frequency());
        assertEquals(AttachmentDef.Knob.of(PhysicsChains.LIMIT), chain.limit());
        assertEquals("none", chain.hinge());
        assertTrue(chain.notes().isEmpty());
    }

    @Test
    void presetLayersUnderTheChainsOwnKeys() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["tail"],"preset":"tail","limit":30}
                """);

        assertEquals("townstead:tail", chain.preset());
        assertEquals(1.4f, chain.frequency().root());
        assertEquals(0.25f, chain.gravity().root());
        assertEquals(30f, chain.limit().root());
    }

    @Test
    void barePresetPrefersTheAttachmentsNamespace() {
        AttachmentDef.PhysicsChain chain = PhysicsChains.parseChain(JsonParser.parseString("""
                {"bones":["tail"],"preset":"tail"}
                """).getAsJsonObject(), "examplepack", PRESETS);

        assertEquals("examplepack:tail", chain.preset());
        assertEquals(2.0f, chain.frequency().root());
    }

    @Test
    void knobsTakeRootToTipRamps() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["a","b","c"],"frequency":{"root":3,"tip":1},"gravity":{"tip":0.6}}
                """);

        assertEquals(new AttachmentDef.Knob(3f, 1f), chain.frequency());
        assertEquals(2f, chain.frequency().at(0.5f));
        assertEquals(AttachmentDef.Knob.of(0.6f), chain.gravity());
    }

    @Test
    void legacySpringKeepsItsSpeedButNotItsBounce() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["tail"],"stiffness":0.4,"damping":0.8}
                """);

        assertEquals(1.17f, chain.frequency().root(), 0.01f);
        assertEquals(PhysicsChains.DAMPING_RATIO, chain.dampingRatio().root());
        assertFalse(chain.notes().isEmpty());
    }

    @Test
    void legacyClampsFoldIntoOneLimit() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["tail"],"max_angle":20,"min_pitch":-12,"max_pitch":28}
                """);

        assertEquals(28f, chain.limit().root());
    }

    @Test
    void legacyGravityScalesWithDroopAngle() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["tail"],"stiffness":0.4,"gravity":0.3,"droop_angle":20}
                """);

        assertEquals(0.15f, chain.gravity().root(), 0.001f);
    }

    @Test
    void legacyResponseFoldsIntoInertia() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"bones":["ear"],"response":{"vertical":1,"forward":0.4,"lateral":0.4,"turn":0.4}}
                """);

        assertEquals(0.275f, chain.inertia().root(), 0.01f);
    }

    @Test
    void collideDefaultsToEveryColliderAndEmptyMeansNone() {
        assertEquals(PhysicsChains.COLLIDE_ALL, parseChain("""
                {"bones":["tail"]}
                """).collide());
        assertTrue(parseChain("""
                {"bones":["tail"],"collide":[]}
                """).collide().isEmpty());
    }

    private static AttachmentDef.PhysicsChain parseChain(String chain) {
        return PhysicsChains.parseChain(JsonParser.parseString(chain).getAsJsonObject(), "townstead", PRESETS);
    }
}
