package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentPhysicsConfigTest {

    @Test
    void pitchBoundsDefaultToTheLegacyMaxAngleRange() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"physics":{"chains":[{"bones":["tail"],"max_angle":35}]}}
                """);

        assertEquals(-35f, chain.minPitch());
        assertEquals(35f, chain.maxPitch());
        assertEquals(0f, chain.restPitch());
    }

    @Test
    void pitchBoundsCanBeCalibratedIndependently() {
        AttachmentDef.PhysicsChain chain = parseChain("""
                {"physics":{"chains":[{
                  "bones":["tail"],
                  "max_angle":45,
                  "min_pitch":-12,
                  "max_pitch":28,
                  "rest_pitch":8
                }]}}
                """);

        assertEquals(45f, chain.maxAngle());
        assertEquals(-12f, chain.minPitch());
        assertEquals(28f, chain.maxPitch());
        assertEquals(8f, chain.restPitch());
    }

    private static AttachmentDef.PhysicsChain parseChain(String json) {
        return AttachmentServerLoader.parsePhysics(JsonParser.parseString(json).getAsJsonObject()).getFirst();
    }
}
