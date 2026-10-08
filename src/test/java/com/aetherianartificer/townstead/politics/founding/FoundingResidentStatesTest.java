package com.aetherianartificer.townstead.politics.founding;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoundingResidentStatesTest {
    private static final String COURT = """
            {"schema":"townstead:founding_profile/v1","culture":"example:morevani","weight":0.2,
             "resident_states":[
               {"state":"townstead_state:vampire","share":0.6,"amount":[4,8]},
               {"state":"townstead_state:thrall","share":0.5}],
             "faction":{"kind":"example:court","welcomes":["vampire"]}}
            """;

    private static FoundingProfileDefinition parse(String json) {
        return FoundingProfileDefinition.parse(ResourceLocation.tryParse("example:court"),
                JsonParser.parseString(json).getAsJsonObject(), Map.of());
    }

    @Test
    void residentStatesParseShareAndAmount() {
        FoundingProfileDefinition profile = parse(COURT);
        assertEquals(2, profile.residentStates().size());
        FoundingProfileDefinition.ResidentState vampire = profile.residentStates().get(0);
        assertEquals("townstead_state:vampire", vampire.state().toString());
        assertEquals(0.6f, vampire.share());
        assertEquals(4, vampire.min());
        assertEquals(8, vampire.max());
        FoundingProfileDefinition.ResidentState thrall = profile.residentStates().get(1);
        assertEquals(1, thrall.min());
        assertEquals(1, thrall.max());
    }

    @Test
    void factionWelcomesParse() {
        assertEquals(Set.of("vampire"), parse(COURT).faction().welcomes());
    }

    @Test
    void shareOutsideZeroToOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                {"schema":"townstead:founding_profile/v1","resident_states":[{"state":"a:b","share":1.5}]}
                """));
    }
}
