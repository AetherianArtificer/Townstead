package com.aetherianartificer.townstead.pheno.action.types;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SocialOutcomeActionTypeTest {
    private final SocialOutcomeActionType type = new SocialOutcomeActionType();

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void acceptsDirectionalChangesAndParticipantSpecificMemories() {
        assertDoesNotThrow(() -> type.parse(json("""
                {'type':'pheno:social_outcome','source':'example:encouragement',
                 'actor_relationship':[{'quality':'townstead:comfort','amount':1}],
                 'other_relationship':[{'quality':'townstead:trust','amount':2,'half_life_days':90}],
                 'actor_memory':'townstead_social:offered_support',
                 'other_memory':'townstead_social:reassured'}
                """)));
    }

    @Test
    void rejectsEmptyOrAmbiguousWrites() {
        assertThrows(IllegalArgumentException.class, () -> type.parse(json(
                "{'type':'pheno:social_outcome','source':'example:none'}")));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("""
                {'type':'pheno:social_outcome','source':'example:duplicate',
                 'actor_relationship':[
                   {'quality':'townstead:trust','amount':1},
                   {'quality':'townstead:trust','amount':2}]}
                """)));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("""
                {'type':'pheno:social_outcome','source':'example:zero',
                 'other_relationship':[{'quality':'townstead:trust','amount':0}]}
                """)));
        assertThrows(IllegalArgumentException.class, () -> type.parse(json("""
                {'type':'pheno:social_outcome','source':'example:negative_decay',
                 'other_relationship':[{'quality':'townstead:trust','amount':1,'half_life_days':-1}]}
                """)));
    }
}
