package com.aetherianartificer.townstead.storage;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePreferenceTest {

    @Test
    void parsesOrderedBlocksAndTags() {
        StoragePreference preference = StoragePreference.parse(JsonParser.parseString("""
                {"buildings":["example:honey_house"],
                 "preferred":["#example:apiary_storage","minecraft:barrel"]}
                """));

        assertEquals(List.of("example:honey_house"), preference.buildings());
        assertEquals(0, preference.buildingRank("example:honey_house"));
        assertEquals(StoragePreference.FALLBACK_RANK,
                preference.buildingRank("example:apiary"));
        assertEquals(2, preference.preferred().size());
        assertTrue(preference.preferred().get(0).tag());
        assertEquals("example:apiary_storage",
                preference.preferred().get(0).id().toString());
        assertEquals("minecraft:barrel", preference.preferred().get(1).id().toString());
    }

    @Test
    void rejectsAmbiguousShorthand() {
        assertThrows(IllegalArgumentException.class, () -> StoragePreference.parse(
                JsonParser.parseString("[\"minecraft:barrel\"]")));
    }
}
