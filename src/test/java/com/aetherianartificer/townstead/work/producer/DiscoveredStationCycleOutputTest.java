package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoveredStationCycleOutputTest {

    @Test
    void toolWorkActionRunsOnlyOnceAndNeverAgainstAnAlreadyFinishedBoard() {
        assertTrue(ToolWorkActionGate.shouldPerform(true, false, false));
        assertFalse(ToolWorkActionGate.shouldPerform(true, false, true));
        assertFalse(ToolWorkActionGate.shouldPerform(true, true, false));
        assertFalse(ToolWorkActionGate.shouldPerform(false, false, false));
    }

    @Test
    void onlyTheActiveRecipesExactProductCanBeDelivered() {
        DiscoveredRecipe pizza = new DiscoveredRecipe(
                id("pizzadelight:pizza"), StationType.FURNACE_STATION, 1,
                id("pizzadelight:pizza"), 1, 200, false,
                null, 0, List.of(), false, false, null);

        assertTrue(CycleOutputMatcher.matches(
                pizza, id("pizzadelight:pizza"), id("pizzadelight:pizza")));
        assertFalse(CycleOutputMatcher.matches(
                pizza, id("minecraft:brown_mushroom"), id("minecraft:brown_mushroom")),
                "an output from some unrelated discovered recipe is not this cycle's output");
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
