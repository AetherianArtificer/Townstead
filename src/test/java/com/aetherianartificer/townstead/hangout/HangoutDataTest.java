package com.aetherianartificer.townstead.hangout;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangoutDataTest {
    @Test
    void parsesOpenPosturesRolesAndSemanticPerformance() {
        HangoutVenue venue = HangoutData.parseVenue(id("test:tavern"), JsonParser.parseString("""
                {"schema":"townstead:hangout_venue/v1","buildings":["mca:tavern"],
                 "capacity":6,"activities":["test:chat"],"amenities":["seating"]}
                """).getAsJsonObject());
        HangoutSpot spot = HangoutData.parseSpot(id("test:chair"), JsonParser.parseString("""
                {"schema":"townstead:hangout_spot/v1","blocks":["minecraft:oak_stairs","#test:chairs"],
                 "posture":"test:sit","capacity":1,"canonical_offset":[0,1,0],
                 "linked_offsets":[[0,-1,0]]}
                """).getAsJsonObject());
        HangoutActivity activity = HangoutData.parseActivity(id("test:chat"), JsonParser.parseString("""
                {"schema":"townstead:hangout_activity/v1","kind":"socialize",
                 "minimum_participants":2,"maximum_participants":4,"duration_ticks":200,
                 "roles":{"initiator":1,"companion":3},"postures":["test:sit"],
                 "service":{"courses":[{"id":"drinks","kind":"drink","role":"initiator",
                                           "at_ticks":40,"lease_ticks":80}]},
                 "performance":{"id":"test:conversation","channel":"social","duration_ticks":200,
                                "priority":5,"fallback":"stand"}}
                """).getAsJsonObject());

        assertEquals(6, venue.capacity());
        assertEquals(1, spot.blockTags().size());
        assertEquals(1, spot.canonicalOffset().getY());
        assertEquals(java.util.Set.of("initiator", "companion"), activity.roles().keySet());
        assertEquals(id("test:conversation"), activity.performance().id());
        assertEquals("initiator", activity.serviceCourses().get(0).role());
        assertEquals(40, activity.serviceCourses().get(0).atTicks());
        assertEquals(HangoutEmbodiment.VANILLA, spot.adapter());
    }

    @Test
    void rejectsUnknownFieldsAndContradictoryLifetimes() {
        assertThrows(IllegalArgumentException.class, () -> HangoutData.parseVenue(id("test:bad"),
                JsonParser.parseString("""
                        {"schema":"townstead:hangout_venue/v1","buildings":["mca:tavern"],
                         "capacity":2,"mystery":true}
                        """).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> HangoutData.parsePolicy(id("test:bad"),
                JsonParser.parseString("""
                        {"schema":"townstead:hangout_policy/v1","minimum_group":2,"maximum_group":4,
                         "arrival_timeout_ticks":200,"lease_ticks":100}
                        """).getAsJsonObject()));
    }

    @Test
    void policyKeepsBondKindsOpen() {
        HangoutPolicy policy = HangoutData.parsePolicy(id("test:social"), JsonParser.parseString("""
                {"schema":"townstead:hangout_policy/v1","minimum_group":2,"maximum_group":5,
                 "arrival_timeout_ticks":100,"lease_ticks":500,
                 "bond_weights":{"another_mod:rivalry":-10,"another_mod:friendship":40}}
                """).getAsJsonObject());
        assertTrue(policy.bondWeights().containsKey("another_mod:friendship"));
        assertEquals(-10, policy.bondWeights().get("another_mod:rivalry"));
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
