package com.aetherianartificer.townstead.pheno.state;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityStateDefinitionTest {
    @Test
    void parsesCanonicalIdentityTiersAndPolicies() {
        EntityStateDefinition definition = EntityStateDefinition.parse(
                ResourceLocation.tryParse("example:file"), JsonParser.parseString("""
                {
                  "schema":"pheno:entity_state/v1",
                  "id":"townstead_state:drunk",
                  "min":0,
                  "max":6,
                  "tiers":[
                    {"id":"wasted","min":5},
                    {"id":"tipsy","min":1},
                    {"id":"drunk","min":3}
                  ],
                  "merge":"max",
                  "persistence":"persistent",
                  "death":"clear"
                }
                """).getAsJsonObject());

        assertEquals("townstead_state:drunk", definition.id().toString());
        assertEquals("tipsy", definition.tier(2).id());
        assertEquals("drunk", definition.tier(4).id());
        assertEquals("wasted", definition.tier(6).id());
        assertEquals(EntityStateDefinition.MergePolicy.MAX, definition.merge());
        assertEquals(EntityStateDefinition.DeathPolicy.CLEAR, definition.deathPolicy());
    }

    @Test
    void rejectsAmbiguousOrUnversionedDefinitions() {
        assertThrows(IllegalArgumentException.class, () -> EntityStateDefinition.parse(
                ResourceLocation.tryParse("townstead_state:drunk"),
                JsonParser.parseString("{\"min\":0,\"max\":6}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> EntityStateDefinition.parse(
                ResourceLocation.tryParse("townstead_state:drunk"), JsonParser.parseString("""
                {"schema":"pheno:entity_state/v1","min":0,"max":6,
                 "tiers":[{"id":"tipsy","min":1},{"id":"tipsy","min":2}]}
                """).getAsJsonObject()));
    }

    @Test
    void bundledDrunkIdentityUsesCanonicalNamespace() {
        assertNotNull(getClass().getResource("/data/townstead_state/entity_state/drunk.json"));
    }
}
