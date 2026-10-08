package com.aetherianartificer.townstead.pheno.state;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderBackingTest {
    private static StateBacking parse(String json) {
        return StateBacking.parse(ResourceLocation.tryParse("example:backing"),
                JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void providerBackingParsesItsLevelMapping() {
        StateBacking backing = parse("""
                {"schema":"pheno:state_backing/v1","state":"townstead_state:vampire",
                 "source":{"type":"pheno:provider","provider":"vampirism:vampire_level",
                           "levels":{"1":"fledgling","4":"vampire","14":90}}}
                """);
        assertEquals(StateBacking.SourceType.PROVIDER, backing.type());
        assertEquals("vampirism:vampire_level", backing.provider().toString());
        assertFalse(backing.writable());
        assertEquals("fledgling", backing.amplifierLevels().get(1).tier());
        assertEquals(90, backing.amplifierLevels().get(14).amount());
    }

    @Test
    void providerBackingsAreObservationOnlyAndNamed() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"schema":"pheno:state_backing/v1","state":"townstead_state:vampire",
                 "source":{"type":"pheno:provider","provider":"vampirism:vampire_level"},"writable":true}
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"schema":"pheno:state_backing/v1","state":"townstead_state:vampire",
                 "source":{"type":"pheno:provider"}}
                """));
    }

    @Test
    void unregisteredProviderReadsNothing() {
        assertNull(StateProviders.read(ResourceLocation.tryParse("absent_mod:level"), null));
        assertNull(StateProviders.read(null, null));
    }
}
