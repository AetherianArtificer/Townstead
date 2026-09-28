package com.aetherianartificer.townstead.pheno.state;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectStateTest {
    private static final String VAMPIRE = """
            {"schema":"pheno:entity_state/v1","id":"townstead_state:vampire","min":0,"max":100,
             "tiers":[{"id":"turning","min":1},{"id":"fledgling","min":20},{"id":"vampire","min":50},{"id":"elder","min":90}],
             "death":"keep",
             "aspect":{"inheritance":{"mode":"child_aspect","aspect":"townstead_state:dhampir","chance":0.5,"parents":"any"}}}
            """;

    private static EntityStateDefinition vampire() {
        return EntityStateDefinition.parse(ResourceLocation.tryParse("example:vampire"),
                JsonParser.parseString(VAMPIRE).getAsJsonObject());
    }

    private static StateEffect effect(String json) {
        return StateEffect.parse(ResourceLocation.tryParse("example:effect"), JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void aspectBlockParsesInheritance() {
        EntityStateDefinition definition = vampire();
        assertTrue(definition.aspect().display());
        EntityStateDefinition.Inheritance inheritance = definition.aspect().inheritance();
        assertEquals("townstead_state:dhampir", inheritance.aspect().toString());
        assertEquals(0.5, inheritance.chance());
        assertEquals(EntityStateDefinition.Parents.ANY, inheritance.parents());
        assertEquals(1, definition.receivedAmount());
    }

    @Test
    void sameInheritancePointsAtItselfAndNoneLeavesNoRule() {
        EntityStateDefinition same = EntityStateDefinition.parse(ResourceLocation.tryParse("example:curse"),
                JsonParser.parseString("""
                {"schema":"pheno:entity_state/v1","id":"example:curse","min":0,"max":1,
                 "aspect":{"display":false,"inheritance":{"mode":"same","parents":"both"}}}
                """).getAsJsonObject());
        assertEquals("example:curse", same.aspect().inheritance().aspect().toString());
        assertEquals(EntityStateDefinition.Parents.BOTH, same.aspect().inheritance().parents());
        assertFalse(same.aspect().display());
        assertEquals(1, same.receivedAmount());

        EntityStateDefinition none = EntityStateDefinition.parse(ResourceLocation.tryParse("example:none"),
                JsonParser.parseString("""
                {"schema":"pheno:entity_state/v1","min":0,"max":1,"aspect":{"inheritance":{"mode":"none"}}}
                """).getAsJsonObject());
        assertNull(none.aspect().inheritance());
    }

    @Test
    void passingStatesHaveNoAspect() {
        EntityStateDefinition drunk = EntityStateDefinition.parse(ResourceLocation.tryParse("example:drunk"),
                JsonParser.parseString("{\"schema\":\"pheno:entity_state/v1\",\"min\":0,\"max\":6}").getAsJsonObject());
        assertNull(drunk.aspect());
    }

    @Test
    void rejectsMalformedInheritance() {
        assertThrows(IllegalArgumentException.class, () -> EntityStateDefinition.parse(ResourceLocation.tryParse("example:x"),
                JsonParser.parseString("""
                {"schema":"pheno:entity_state/v1","min":0,"max":1,"aspect":{"inheritance":{"mode":"child_aspect"}}}
                """).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> EntityStateDefinition.parse(ResourceLocation.tryParse("example:x"),
                JsonParser.parseString("""
                {"schema":"pheno:entity_state/v1","min":0,"max":1,"aspect":{"inheritance":{"mode":"same","chance":2}}}
                """).getAsJsonObject()));
    }

    @Test
    void minTierAdmitsItsTierAndEveryTierAbove() {
        EntityStateDefinition definition = vampire();
        StateEffect effect = effect("""
                {"schema":"pheno:state_effect/v1","state":"townstead_state:vampire","min_tier":"fledgling",
                 "genes":{"grant":["townstead_roots:sun_burn"]}}
                """);
        assertFalse(effect.admitsTier(definition, "turning", 0));
        assertTrue(effect.admitsTier(definition, "fledgling", 1));
        assertTrue(effect.admitsTier(definition, "elder", 3));
    }

    @Test
    void exactTierStillMatchesOnlyItself() {
        EntityStateDefinition definition = vampire();
        StateEffect effect = effect("""
                {"schema":"pheno:state_effect/v1","state":"townstead_state:vampire","tier":"vampire",
                 "genes":{"suppress":["townstead_roots:hunger"]}}
                """);
        assertTrue(effect.admitsTier(definition, "vampire", 2));
        assertFalse(effect.admitsTier(definition, "elder", 3));
    }

    @Test
    void genesParseGrantsWithVariantsAndSuppressions() {
        StateEffect effect = effect("""
                {"schema":"pheno:state_effect/v1","state":"townstead_state:vampire",
                 "genes":{"grant":["townstead_roots:sun_burn",{"gene":"townstead_roots:skin_tint","variant":"pallid"}],
                          "suppress":["townstead_roots:hunger"]}}
                """);
        assertEquals(2, effect.genes().grant().size());
        assertNull(effect.genes().grant().get(0).variant());
        assertEquals("pallid", effect.genes().grant().get(1).variant());
        assertEquals("townstead_roots:hunger", effect.genes().suppress().get(0).toString());
    }

    @Test
    void rejectsAmbiguousOrEmptyGeneEffects() {
        assertThrows(IllegalArgumentException.class, () -> effect("""
                {"schema":"pheno:state_effect/v1","state":"townstead_state:vampire","tier":"vampire","min_tier":"fledgling",
                 "genes":{"grant":["townstead_roots:sun_burn"]}}
                """));
        assertThrows(IllegalArgumentException.class, () -> effect("""
                {"schema":"pheno:state_effect/v1","state":"townstead_state:vampire","genes":{}}
                """));
    }

    @Test
    void stateExcludesParse() {
        EntityStateDefinition thrall = EntityStateDefinition.parse(ResourceLocation.tryParse("example:thrall"),
                JsonParser.parseString("""
                        {"schema":"pheno:entity_state/v1","id":"townstead_state:thrall","excludes":["townstead_state:vampire"]}
                        """).getAsJsonObject());
        assertTrue(thrall.excludes().contains(ResourceLocation.tryParse("townstead_state:vampire")));
    }

    @Test
    void inheritanceCanRequireOnlyTheFather() {
        EntityStateDefinition vampire = EntityStateDefinition.parse(ResourceLocation.tryParse("example:vampire"),
                JsonParser.parseString("""
                        {"schema":"pheno:entity_state/v1","id":"townstead_state:vampire",
                         "aspect":{"inheritance":{"mode":"child_aspect","aspect":"townstead_state:dhampir","parents":"father_only"}}}
                        """).getAsJsonObject());
        assertEquals(EntityStateDefinition.Parents.FATHER_ONLY, vampire.aspect().inheritance().parents());
    }
}
