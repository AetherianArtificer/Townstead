package com.aetherianartificer.townstead.compat.weaversparadise;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WeaversParadiseStationAdapterTest {

    @Test
    void rankMapsOntoTheRecipeScoreRange() {
        assertEquals(0, WeaversParadiseStationAdapter.scoreFor(1, 5, 10));
        assertEquals(5, WeaversParadiseStationAdapter.scoreFor(3, 5, 10));
        assertEquals(10, WeaversParadiseStationAdapter.scoreFor(5, 5, 10));
        assertEquals(10, WeaversParadiseStationAdapter.scoreFor(9, 5, 10));
        assertEquals(10, WeaversParadiseStationAdapter.scoreFor(1, 1, 10));
        assertEquals(0, WeaversParadiseStationAdapter.scoreFor(3, 5, 0));
    }

    /** Tier results stay null: an ItemStack needs registries, and nothing here reads the stack. */
    @Test
    void tiersResolveByScoreWithGapsFallingToTheTierBelow() {
        WeaversParadiseRecipes.Tier low = new WeaversParadiseRecipes.Tier(0, 1, null, 1);
        WeaversParadiseRecipes.Tier mid = new WeaversParadiseRecipes.Tier(2, 3, null, 1);
        WeaversParadiseRecipes.Tier top = new WeaversParadiseRecipes.Tier(5, 6, null, 2);
        WeaversParadiseRecipes.ClothcraftingInfo info = new WeaversParadiseRecipes.ClothcraftingInfo(
                List.of(low, mid, top), null, 6);

        assertSame(top, info.best());
        assertEquals(6, info.maxScore());
        assertSame(low, info.forScore(0));
        assertSame(mid, info.forScore(3));
        assertSame(mid, info.forScore(4));
        assertSame(top, info.forScore(6));
        assertSame(top, info.forScore(9));
        assertNotNull(info.forScore(-1));
    }
}
