package com.aetherianartificer.townstead.compat.mca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingBlockQuantityTest {
    @Test
    void bakeryStackPropertyRepresentsEveryBottleInTheTile() {
        assertEquals(2, BuildingBlockQuantity.units("stack", 2));
        assertEquals(4, BuildingBlockQuantity.units("stack", 4));
    }

    @Test
    void unrelatedPropertiesRemainOneBuildingBlock() {
        assertEquals(1, BuildingBlockQuantity.units("lit", true));
        assertEquals(1, BuildingBlockQuantity.units("age", 3));
    }

    @Test
    void malformedStackValuesCannotRemoveTheBlockUnit() {
        assertEquals(1, BuildingBlockQuantity.units("stack", 0));
        assertEquals(1, BuildingBlockQuantity.units("stack", "2"));
    }
}
