package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.NothingActionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.EntityTypeConditionType;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateContributionTest {
    @BeforeAll
    static void registerPhenoVocabulary() {
        ConditionTypes.register(new EntityTypeConditionType("pheno:entity_type"));
        ActionTypes.register(new NothingActionType());
    }

    @Test
    void ownedBackingIsWritableAndStatusBackingIsRegistryIndependent() {
        StateBacking owned = StateBacking.parse(ResourceLocation.tryParse("townstead:owned"),
                JsonParser.parseString("""
                {"schema":"pheno:state_backing/v1","state":"townstead_state:drunk",
                 "source":{"type":"pheno:owned"},"writable":true}
                """).getAsJsonObject());
        StateBacking observed = StateBacking.parse(ResourceLocation.tryParse("brewery:drunk"),
                JsonParser.parseString("""
                {"schema":"pheno:state_backing/v1","state":"townstead_state:drunk",
                 "applies_to":{"type":"pheno:entity_type","entity_type":"minecraft:player"},
                 "source":{"type":"pheno:status_effect","effect":"absent_mod:drunk",
                           "amplifier":{"0":"tipsy","2":3}},"writable":false}
                """).getAsJsonObject());

        assertTrue(owned.writable());
        assertFalse(observed.writable());
        assertEquals("absent_mod:drunk", observed.statusEffect().toString());
        assertEquals("tipsy", observed.amplifierLevels().get(0).tier());
        assertEquals(3, observed.amplifierLevels().get(2).amount());
    }

    @Test
    void statusEffectWritesFailClosedInV1() {
        assertThrows(IllegalArgumentException.class, () -> StateBacking.parse(
                ResourceLocation.tryParse("brewery:drunk"), JsonParser.parseString("""
                {"schema":"pheno:state_backing/v1","state":"townstead_state:drunk",
                 "source":{"type":"pheno:status_effect","effect":"brewery:drunk"},
                 "writable":true}
                """).getAsJsonObject()));
    }

    @Test
    void independentStateEffectParsesAllTransitionHooks() {
        StateEffect effect = StateEffect.parse(ResourceLocation.tryParse("example:drunk_fun"),
                JsonParser.parseString("""
                {"schema":"pheno:state_effect/v1","state":"townstead_state:drunk","tier":"tipsy",
                 "priority":12,
                 "on_enter":{"type":"pheno:nothing"},
                 "on_tier_change":{"type":"pheno:nothing"},
                 "while_active":{"interval":40,"chance":0.25,"do":{"type":"pheno:nothing"}},
                 "on_exit":{"type":"pheno:nothing"}}
                """).getAsJsonObject());

        assertNotNull(effect.onEnter());
        assertNotNull(effect.onTierChange());
        assertNotNull(effect.onExit());
        assertEquals(40, effect.whileActive().interval());
        assertEquals(0.25, effect.whileActive().chance());
    }
}
