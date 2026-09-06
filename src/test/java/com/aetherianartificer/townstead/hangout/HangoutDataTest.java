package com.aetherianartificer.townstead.hangout;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangoutDataTest {
    @Test
    void parsesOpenPosturesRolesAndSemanticPerformance() {
        HangoutVenue venue = HangoutData.parseVenue(id("test:tavern"), JsonParser.parseString("""
                {"schema":"townstead:hangout_venue/v2","buildings":["mca:tavern"],
                 "capacity":6,"activities":["test:chat"],
                 "tags":["test:lively","test:social"],"amenities":["seating"]}
                """).getAsJsonObject());
        HangoutSpot spot = HangoutData.parseSpot(id("test:chair"), JsonParser.parseString("""
                {"schema":"townstead:hangout_spot/v1","blocks":["minecraft:oak_stairs","#test:chairs"],
                 "posture":"test:sit","capacity":1,"canonical_offset":[0,1,0],
                 "linked_offsets":[[0,-1,0]]}
                """).getAsJsonObject());
        HangoutActivity activity = HangoutData.parseActivity(id("test:chat"), JsonParser.parseString("""
                {"schema":"townstead:hangout_activity/v2","kind":"socialize",
                 "minimum_participants":2,"maximum_participants":4,"duration_ticks":200,
                 "roles":{"initiator":1,"companion":3},"postures":["test:sit"],
                 "service":{"courses":[{"id":"drinks","kind":"drink","role":"bartender",
                                           "at_ticks":40,"lease_ticks":80}]},
                 "social_cues":{"dialogue_intent":"social:tavern","dialogue_interval_ticks":180,
                   "expressions":["test:smile","test:question"],"expression_interval_ticks":100},
                 "performance":{"id":"test:conversation","channel":"social","duration_ticks":200,
                                "priority":5,"fallback":"stand"}}
                """).getAsJsonObject());

        assertEquals(6, venue.capacity());
        assertEquals(java.util.Set.of(id("test:lively"), id("test:social")), venue.tags());
        assertEquals(1, spot.blockTags().size());
        assertEquals(1, spot.canonicalOffset().getY());
        assertEquals(java.util.Set.of("initiator", "companion"), activity.roles().keySet());
        assertEquals(id("test:conversation"), activity.performance().id());
        assertEquals("bartender", activity.serviceCourses().get(0).role());
        assertEquals(40, activity.serviceCourses().get(0).atTicks());
        assertEquals("social:tavern", activity.socialCues().dialogueIntent());
        assertEquals(java.util.List.of(id("test:smile"), id("test:question")),
                activity.socialCues().expressions());
        assertEquals(HangoutEmbodiment.VANILLA, spot.adapter());
    }

    @Test
    void rejectsUnknownFieldsAndContradictoryLifetimes() {
        assertThrows(IllegalArgumentException.class, () -> HangoutData.parseVenue(id("test:bad"),
                JsonParser.parseString("""
                        {"schema":"townstead:hangout_venue/v2","buildings":["mca:tavern"],
                         "capacity":2,"mystery":true}
                        """).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> HangoutData.parsePolicy(id("test:bad"),
                JsonParser.parseString("""
                        {"schema":"townstead:hangout_policy/v2","minimum_visit_ticks":400,
                         "maximum_visit_ticks":200,"arrival_timeout_ticks":200,"lease_ticks":800}
                        """).getAsJsonObject()));
    }

    @Test
    void policyKeepsBondKindsOpen() {
        HangoutPolicy policy = HangoutData.parsePolicy(id("test:social"), JsonParser.parseString("""
                {"schema":"townstead:hangout_policy/v2",
                 "arrival_timeout_ticks":100,"lease_ticks":500,
                 "bond_weights":{"another_mod:rivalry":-10,"another_mod:friendship":40}}
                """).getAsJsonObject());
        assertTrue(policy.bondWeights().containsKey("another_mod:friendship"));
        assertEquals(-10, policy.bondWeights().get("another_mod:rivalry"));
    }

    @Test
    void personalityVenueTagsAreSoftPositiveMultipliers() {
        HangoutVenue venue = HangoutData.parseVenue(id("test:tea_house"), JsonParser.parseString("""
                {"schema":"townstead:hangout_venue/v2","buildings":["test:tea_house"],
                 "tags":["test:quiet","test:tea"]}
                """).getAsJsonObject());
        HangoutPolicy policy = HangoutData.parsePolicy(id("test:social"), JsonParser.parseString("""
                {"schema":"townstead:hangout_policy/v2",
                 "arrival_timeout_ticks":100,"lease_ticks":500,
                 "personality_tag_weights":{
                   "introverted":{"test:quiet":2.0,"test:tea":1.5},
                   "default":{"test:quiet":1.2}
                 }}
                """).getAsJsonObject());

        assertEquals(1.75D, HangoutPreferences.affinity(venue, policy, "mca:introverted"), 0.0001D);
        assertEquals(1.2D, HangoutPreferences.affinity(venue, policy, "unknown:addon"), 0.0001D);
        assertThrows(IllegalArgumentException.class, () -> HangoutData.parsePolicy(id("test:bad"),
                JsonParser.parseString("""
                        {"schema":"townstead:hangout_policy/v2",
                         "personality_tag_weights":{"playful":{"test:lively":0}}}
                        """).getAsJsonObject()));
    }

    @Test
    void policyDefinesIndividualVisitAndSocialBeatTimingSeparately() {
        HangoutPolicy policy = HangoutData.parsePolicy(id("test:social"), JsonParser.parseString("""
                {"schema":"townstead:hangout_policy/v2","social_radius":18,
                 "minimum_visit_ticks":500,"maximum_visit_ticks":1600,
                 "revisit_cooldown_ticks":2400,"retry_cooldown_ticks":100,
                 "arrival_timeout_ticks":300,"lease_ticks":900}
                """).getAsJsonObject());
        assertEquals(18, policy.socialRadius());
        assertEquals(500, policy.minimumVisitTicks());
        assertEquals(1600, policy.maximumVisitTicks());
        assertEquals(2400, policy.revisitCooldownTicks());
        assertEquals(100, policy.retryCooldownTicks());
    }

    @Test
    void venueRejectsMissingActivitiesAndUndeclaredServiceStaff() {
        HangoutVenue venue = HangoutData.parseVenue(id("test:tavern"), JsonParser.parseString("""
                {"schema":"townstead:hangout_venue/v2","buildings":["inn"],
                 "activities":["test:round"]}
                """).getAsJsonObject());
        HangoutActivity round = HangoutData.parseActivity(id("test:round"), JsonParser.parseString("""
                {"schema":"townstead:hangout_activity/v2","kind":"drink",
                 "minimum_participants":2,"maximum_participants":4,"duration_ticks":200,
                 "roles":{"patron":4},
                 "service":{"courses":[{"role":"bartender","lease_ticks":80}]}}
                """).getAsJsonObject());

        assertTrue(HangoutData.venueContractViolation(venue, java.util.Map.of())
                .contains("missing activity"));
        assertTrue(HangoutData.venueContractViolation(venue, java.util.Map.of(round.id(), round))
                .contains("undeclared staff role"));

        HangoutVenue staffed = new HangoutVenue(venue.id(), venue.buildings(), venue.capacity(),
                venue.activities(), venue.tags(), venue.amenities(),
                java.util.Map.of("bartender", ignored -> true), null, null);
        assertNull(HangoutData.venueContractViolation(staffed, java.util.Map.of(round.id(), round)));
    }

    @Test
    void parsesAdmissionParticipationAndPerCourseServiceEligibilitySeparately() {
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LifeStageConditionType());
        HangoutVenue venue = HangoutData.parseVenue(id("test:adult_lounge"), JsonParser.parseString("""
                {"schema":"townstead:hangout_venue/v2","buildings":["test:lounge"],
                 "admission_when":{"type":"pheno:life_stage","tag":"townstead_lifecycle:adult"}}
                """).getAsJsonObject());
        HangoutActivity activity = HangoutData.parseActivity(id("test:cocktail_round"),
                JsonParser.parseString("""
                {"schema":"townstead:hangout_activity/v2","kind":"drink",
                 "minimum_participants":2,"maximum_participants":4,"duration_ticks":200,
                 "participant_when":{"type":"pheno:life_stage","tag":"townstead_lifecycle:can_consume_alcohol"},
                 "service":{"courses":[{"id":"cocktail","kind":"drink","role":"bartender",
                   "eligible_when":{"type":"pheno:life_stage","tag":"townstead_lifecycle:can_consume_alcohol"}}]}}
                """).getAsJsonObject());

        assertNotNull(venue.admissionWhen());
        assertNotNull(activity.participantWhen());
        assertNotNull(activity.serviceCourses().get(0).eligibleWhen());
    }

    @Test
    void professionPathConditionDoesNotPretendHistoricalSubjectsCarryPathState() {
        var type = new com.aetherianartificer.townstead.pheno.condition.types.ProfessionConditionType();
        assertTrue(type.parse(JsonParser.parseString("""
                {"profession":"townstead:beverage_artisan"}
                """).getAsJsonObject()).supportsSubject());
        assertFalse(type.parse(JsonParser.parseString("""
                {"profession":"townstead:beverage_artisan","path":"bartender"}
                """).getAsJsonObject()).supportsSubject());
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
