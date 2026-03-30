package com.aetherianartificer.townstead.emote;

import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteReactionSchemaValidatorTest {

    @Test
    void acceptsSingleTriggerEmote() {
        JsonObject json = JsonParser.parseString("""
                {
                  "id": "wave",
                  "trigger_emote": "waving",
                  "radius": 8,
                  "cooldown_ticks": 120,
                  "candidates": [
                    {
                      "chat_key_prefix": "dialogue.chat.emote_reaction.greeting",
                      "chat_variants": 4,
                      "weight": 3
                    }
                  ]
                }
                """).getAsJsonObject();

        assertTrue(EmoteReactionSchemaValidator.validate(json).isEmpty());
    }

    @Test
    void acceptsValidReactionDefinition() {
        JsonObject json = JsonParser.parseString("""
                {
                  "id": "wave",
                  "trigger_emotes": ["waving", "33b912f8-0aa0-45e6-a2d4-9b5677e6f35c"],
                  "radius": 8,
                  "cooldown_ticks": 120,
                  "candidates": [
                    {
                      "villager_emote": "wave_back",
                      "chat_key_prefix": "dialogue.chat.emote_reaction.greeting",
                      "chat_variants": 4,
                      "chat_chance": 0.4,
                      "weight": 3,
                      "personality_weights": {
                        "friendly": 8
                      }
                    }
                  ]
                }
                """).getAsJsonObject();

        assertTrue(EmoteReactionSchemaValidator.validate(json).isEmpty());
    }

    @Test
    void rejectsMissingTriggerAndInvalidCandidate() {
        JsonObject json = JsonParser.parseString("""
                {
                  "id": "broken",
                  "candidates": [
                    {
                      "weight": 0,
                      "chat_key_prefix": "bad.prefix"
                    }
                  ]
                }
                """).getAsJsonObject();

        assertFalse(EmoteReactionSchemaValidator.validate(json).isEmpty());
    }
}
