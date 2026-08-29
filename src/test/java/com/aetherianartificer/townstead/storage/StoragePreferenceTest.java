package com.aetherianartificer.townstead.storage;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoragePreferenceTest {

    @AfterEach
    void resetBuildingRoles() {
        BuildingStorageRoles.replaceAll(Map.of());
    }

    @Test
    void parsesAndRanksSemanticBuildingRoles() {
        ResourceLocation materials = ResourceLocation.tryParse("townstead:materials");
        BuildingStorageRoles.replaceAll(Map.of(
                "warehouse", Set.of(materials),
                "storage", Set.of(BuildingStorageRoles.GENERAL)));
        StoragePreference preference = StoragePreference.parse(JsonParser.parseString("""
                {"preferred_roles":["townstead:materials"]}
                """));

        assertEquals(List.of(materials), preference.preferredRoles());
        assertEquals(StoragePreference.EXTERNAL_BASE_RANK,
                preference.buildingRank("warehouse"));
        assertEquals(StoragePreference.EXTERNAL_BASE_RANK + 1,
                preference.buildingRank("storage"));
        assertEquals(StoragePreference.FALLBACK_RANK,
                preference.buildingRank("example:apiary"));
    }

    @Test
    void noProfessionPreferenceStillFindsGeneralStorage() {
        BuildingStorageRoles.replaceAll(Map.of(
                "storage", Set.of(BuildingStorageRoles.GENERAL)));

        assertEquals(StoragePreference.EXTERNAL_BASE_RANK,
                StoragePreference.NONE.buildingRank("storage"));
    }

    @Test
    void rejectsAmbiguousShorthand() {
        assertThrows(IllegalArgumentException.class, () -> StoragePreference.parse(
                JsonParser.parseString("[\"minecraft:barrel\"]")));
        assertThrows(IllegalArgumentException.class, () -> StoragePreference.parse(
                JsonParser.parseString("{\"preferred\":[\"minecraft:barrel\"]}")));
    }
}
