package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.PerformanceActionType;
import com.aetherianartificer.townstead.pheno.action.types.SpeakActionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.EntityStateConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.LifeStageConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.ProfessionConditionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoredTavernDataTest {
    @BeforeAll
    static void registerVocabulary() {
        ConditionTypes.register(new NumericConditionType("pheno:hunger", ignored -> 0));
        ConditionTypes.register(new NumericConditionType("pheno:thirst", ignored -> 0));
        ConditionTypes.register(new NumericConditionType("pheno:energy", ignored -> 0));
        ConditionTypes.register(new EntityStateConditionType());
        ConditionTypes.register(new LogicConditionType("pheno:or", LogicConditionType.Mode.OR));
        ConditionTypes.register(new ProfessionConditionType());
        ConditionTypes.register(new LifeStageConditionType());
        ActionTypes.register(new PerformanceActionType());
        ActionTypes.register(new SpeakActionType());
    }

    @Test
    void tavernKeepsNeedDrivenActivitiesAheadOfConversationFallback() throws Exception {
        HangoutVenue venue = HangoutData.parseVenue(id("townstead:tavern"), resource(
                "/data/townstead/hangout_venue/tavern.json"));

        assertEquals(java.util.Set.of("inn"), venue.buildings());
        assertTrue(venue.staffRoles().containsKey("bartender"));
        assertTrue(venue.staffRoles().containsKey("server"));
        assertEquals(java.util.List.of(
                id("townstead:tavern_shared_meal"),
                id("townstead:tavern_round"),
                id("townstead:tavern_supper"),
                id("townstead:tavern_conversation")), venue.activities());
    }

    @Test
    void drinkAndSupperActivitiesParseRefusalPolicyAndSemanticCues() throws Exception {
        HangoutActivity round = HangoutData.parseActivity(id("townstead:tavern_round"), resource(
                "/data/townstead/hangout_activity/tavern_round.json"));
        HangoutActivity supper = HangoutData.parseActivity(id("townstead:tavern_supper"), resource(
                "/data/townstead/hangout_activity/tavern_supper.json"));

        assertEquals(HangoutActivity.Kind.DRINK, round.kind());
        assertNull(round.startWhen(), "a recreational round must not require thirst");
        assertNotNull(round.participantWhen());
        assertNotNull(round.serviceWhen());
        assertNotNull(round.serviceCourses().get(0).eligibleWhen());
        assertNotNull(round.onServiceAccepted());
        assertNotNull(round.onServiceRefused());
        assertEquals(id("townstead:round_at_the_table"), round.performance().id());
        assertEquals(java.util.Set.of("patron"), round.roles().keySet());
        assertEquals("bartender", round.serviceCourses().get(0).role());
        assertEquals(HangoutActivity.Kind.MIXED, supper.kind());
        assertNotNull(supper.startWhen());
        assertNotNull(supper.serviceWhen());
        assertNotNull(supper.onServiceRefused());
        assertNotNull(supper.serviceCourses().get(0).eligibleWhen(),
                "the alcoholic table-drink course needs its own life-stage gate");
        assertTrue(supper.serviceCourses().get(1).eligibleWhen() == null,
                "the same guest must still be eligible for the supper course");
    }

    @Test
    void ordinaryHangoutsDoNotRecruitGuardsOrArchers() throws Exception {
        JsonObject authored = resource("/data/townstead/hangout_policy/ordinary_social.json");
        HangoutPolicy policy = HangoutData.parsePolicy(id("townstead:ordinary_social"), authored);
        JsonObject dutyTag = resource(
                "/data/townstead_hangouts/tags/villager_profession/on_duty.json");

        assertNotNull(policy.visitorWhen());
        assertNotNull(policy.companionWhen());
        assertTrue(policy.personalityTagWeights().containsKey("introverted"));
        assertTrue(policy.personalityTagWeights().containsKey("extroverted"));
        assertTrue(policy.venueRadius() >= 256,
                "MCA home-village membership is the catchment; radius is only a safety cap");
        assertEquals("#townstead_hangouts:on_duty", authored.getAsJsonObject("visitor_when")
                .get("profession").getAsString());
        assertEquals("#townstead_hangouts:on_duty", authored.getAsJsonObject("companion_when")
                .get("profession").getAsString());
        assertTrue(dutyTag.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> "mca:guard".equals(value.getAsString())));
        assertTrue(dutyTag.getAsJsonArray("values").asList().stream()
                .anyMatch(value -> "mca:archer".equals(value.getAsString())));
    }

    @Test
    void cocktailBarHasAGroupYieldingSoloFallback() throws Exception {
        HangoutVenue venue = HangoutData.parseVenue(id("townstead:beachparty_cocktail_bar"),
                resource("/data/townstead/hangout_venue/beachparty_cocktail_bar.json"));
        HangoutActivity solo = HangoutData.parseActivity(id("townstead:beachparty_quiet_lounge"),
                resource("/data/townstead/hangout_activity/beachparty_quiet_lounge.json"));
        HangoutActivity cocktails = HangoutData.parseActivity(id("townstead:beachparty_cocktail_round"),
                resource("/data/townstead/hangout_activity/beachparty_cocktail_round.json"));

        assertTrue(venue.activities().contains(solo.id()));
        assertTrue(venue.tags().contains(id("townstead_hangouts:family_friendly")),
                "the venue admits families even though its alcohol activity is restricted");
        assertTrue(venue.admissionWhen() == null,
                "the Cabana itself must not be adult-only");
        assertEquals(1, solo.minimumParticipants());
        assertTrue(solo.participantWhen() == null,
                "child-safe quiet leisure must remain available");
        assertTrue(solo.yieldToGroups());
        assertNotNull(solo.onStart(), "a lone patron should have visible venue behavior");
        assertNotNull(cocktails.participantWhen());
        assertNotNull(cocktails.serviceCourses().get(0).eligibleWhen());
    }

    @Test
    void everyAuthoredVenueAndBeatUsesTheIndividualAttendanceSchemas() throws Exception {
        java.util.Map<String, HangoutVenue> venues = new java.util.LinkedHashMap<>();
        for (String name : java.util.List.of(
                "beachparty_beach_club", "beachparty_cocktail_bar", "brewery_brew_hall",
                "brewinandchewin_taproom", "candlelight_restaurant", "herbal_brews_tea_house",
                "kaleidoscope_tavern", "tavern", "vinery_winery_tasting")) {
            JsonObject json = resource("/data/townstead/hangout_venue/" + name + ".json");
            assertEquals(HangoutData.VENUE_SCHEMA, json.get("schema").getAsString(), name);
            venues.put(name, HangoutData.parseVenue(id("townstead:" + name), json));
        }

        java.util.Map<ResourceLocation, HangoutActivity> activities = new java.util.LinkedHashMap<>();
        for (String name : java.util.List.of(
                "beachparty_cocktail_round", "beachparty_quiet_lounge", "beachparty_radio_listening",
                "candlelight_after_dinner", "candlelight_dinner", "herbal_brews_cafe_conversation",
                "herbal_brews_coffee_break", "herbal_brews_quiet_tea", "herbal_brews_tasting",
                "kaleidoscope_bar_conversation", "kaleidoscope_tasting", "tavern_conversation",
                "tavern_round", "tavern_shared_meal", "tavern_supper")) {
            JsonObject json = resource("/data/townstead/hangout_activity/" + name + ".json");
            assertEquals(HangoutData.ACTIVITY_SCHEMA, json.get("schema").getAsString(), name);
            activities.put(id("townstead:" + name),
                    HangoutData.parseActivity(id("townstead:" + name), json));
        }

        for (HangoutVenue venue : venues.values()) {
            assertTrue(!venue.tags().isEmpty(), venue.id() + " needs semantic preference tags");
            assertTrue(venue.tags().stream().allMatch(tag -> "townstead_hangouts".equals(tag.getNamespace())),
                    venue.id() + " must use the open townstead_hangouts vocabulary namespace");
            for (ResourceLocation activityId : venue.activities()) {
                HangoutActivity activity = activities.get(activityId);
                assertNotNull(activity, venue.id() + " references " + activityId);
                for (HangoutActivity.ServiceCourse course : activity.serviceCourses()) {
                    assertTrue(venue.staffRoles().containsKey(course.role()),
                            venue.id() + " lacks staff role " + course.role());
                    assertTrue(!activity.roles().containsKey(course.role()),
                            course.role() + " must not be assigned to a visitor");
                }
            }
        }
    }

    private static JsonObject resource(String path) throws Exception {
        try (var stream = AuthoredTavernDataTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
