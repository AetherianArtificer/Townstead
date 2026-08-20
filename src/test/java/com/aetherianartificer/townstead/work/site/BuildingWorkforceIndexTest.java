package com.aetherianartificer.townstead.work.site;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BuildingWorkforceIndexTest {
    private static final ResourceLocation COOK = ResourceLocation.tryParse("townstead:cook");
    private static final ResourceLocation BAKER = ResourceLocation.tryParse("townstead:baker");

    @AfterEach
    void clear() {
        BuildingWorkforceIndex.replaceAll(Map.of());
    }

    @Test
    void buildingOwnsItsAcceptedProfessions() {
        BuildingWorkforceIndex.replaceAll(Map.of("mill", List.of(COOK, BAKER)));

        assertTrue(BuildingWorkforceIndex.defines("mill"));
        assertTrue(BuildingWorkforceIndex.accepts("mill", COOK));
        assertTrue(BuildingWorkforceIndex.accepts("mill", BAKER));
        assertFalse(BuildingWorkforceIndex.accepts("mill",
                ResourceLocation.tryParse("minecraft:fletcher")));
        assertFalse(BuildingWorkforceIndex.defines("kitchen"));
    }

    @Test
    void explicitEmptyWorkforceDiffersFromLegacyFallback() {
        BuildingWorkforceIndex.replaceAll(Map.of("closed_shop", List.of()));

        assertTrue(BuildingWorkforceIndex.defines("closed_shop"));
        assertFalse(BuildingWorkforceIndex.accepts("closed_shop", COOK));
    }
}
