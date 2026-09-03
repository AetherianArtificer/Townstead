package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.PerformanceActionType;
import com.aetherianartificer.townstead.pheno.action.types.SpeakActionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.EntityStateConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.NumericConditionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthoredTavernDataTest {
    @BeforeAll
    static void registerVocabulary() {
        ConditionTypes.register(new NumericConditionType("pheno:hunger", ignored -> 0));
        ConditionTypes.register(new NumericConditionType("pheno:thirst", ignored -> 0));
        ConditionTypes.register(new EntityStateConditionType());
        ConditionTypes.register(new LogicConditionType("pheno:or", LogicConditionType.Mode.OR));
        ActionTypes.register(new PerformanceActionType());
        ActionTypes.register(new SpeakActionType());
    }

    @Test
    void tavernKeepsNeedDrivenActivitiesAheadOfConversationFallback() throws Exception {
        HangoutVenue venue = HangoutData.parseVenue(id("townstead:tavern"), resource(
                "/data/townstead/hangout_venue/tavern.json"));

        assertEquals(java.util.Set.of("inn"), venue.buildings());
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
        assertNotNull(round.serviceWhen());
        assertNotNull(round.onServiceAccepted());
        assertNotNull(round.onServiceRefused());
        assertEquals(id("townstead:round_at_the_table"), round.performance().id());
        assertEquals(HangoutActivity.Kind.MIXED, supper.kind());
        assertNotNull(supper.startWhen());
        assertNotNull(supper.serviceWhen());
        assertNotNull(supper.onServiceRefused());
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
