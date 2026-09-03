package com.aetherianartificer.townstead.food;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumptionPolicyTest {
    @Test
    void parsesTransactionEnvelope() {
        ConsumptionPolicy policy = parse("""
                {
                  "mode": "replace_with_pheno",
                  "accounting": "native_result",
                  "consumers": ["player", "villager"],
                  "servings": 3,
                  "remainder": {
                    "mode": "item",
                    "item": "example:tankard",
                    "destination": "storage"
                  },
                  "effect_admission": {
                    "default": "allow",
                    "deny": ["teleport", "loot_economy", "player_only"]
                  }
                }
                """);

        assertEquals(ConsumptionPolicy.Mode.REPLACE_WITH_PHENO, policy.mode());
        assertEquals(ConsumptionPolicy.Accounting.NATIVE_RESULT, policy.accounting());
        assertTrue(policy.permits(ConsumptionPolicy.Consumer.VILLAGER));
        assertFalse(policy.permits(ConsumptionPolicy.Consumer.OTHER_LIVING));
        assertEquals(3, policy.servings());
        assertEquals("example:tankard", policy.remainder().item().toString());
        assertEquals(ConsumptionPolicy.RemainderDestination.STORAGE,
                policy.remainder().destination());
        assertEquals(ConsumptionPolicy.Decision.DENY,
                policy.effectAdmission().decision(ConsumptionPolicy.EffectClass.PLAYER_ONLY));
        assertEquals(ConsumptionPolicy.Decision.ALLOW,
                policy.effectAdmission().decision(ConsumptionPolicy.EffectClass.STATUS));
    }

    @Test
    void suppliesConservativeStructuralDefaults() {
        ConsumptionPolicy policy = parse("{}");

        assertEquals(ConsumptionPolicy.Mode.OBSERVE_NATIVE, policy.mode());
        assertEquals(ConsumptionPolicy.Accounting.CONSUME_ONE, policy.accounting());
        assertEquals(1, policy.servings());
        assertEquals(ConsumptionPolicy.RemainderMode.NATIVE, policy.remainder().mode());
        assertEquals(ConsumptionPolicy.RemainderDestination.SOURCE,
                policy.remainder().destination());
        assertTrue(policy.permits(ConsumptionPolicy.Consumer.PLAYER));
        assertTrue(policy.permits(ConsumptionPolicy.Consumer.VILLAGER));
    }

    @Test
    void rejectsAmbiguousOrOutOfScopePolicy() {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"servings\":0}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"remainder\":{\"mode\":\"item\"}}"));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"effect_admission":{"allow":["teleport"],"deny":["teleport"]}}
                """));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"alcoholic\":true}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"strength\":1.0}"));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"future_guess\":true}"));
    }

    private static ConsumptionPolicy parse(String json) {
        return ConsumptionPolicy.parse(JsonParser.parseString(json));
    }
}
