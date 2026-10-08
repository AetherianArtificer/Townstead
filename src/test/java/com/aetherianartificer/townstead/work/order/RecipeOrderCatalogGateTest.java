package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.station.WorkstationDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order sheet must obey the same station-scoped declarations as production. */
class RecipeOrderCatalogGateTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static WorkTaskDef task(String block, String output) {
        return new WorkTaskDef(
                id("townstead_work:cook"),
                new WorkTaskDef.TargetSet(Set.of(id(block)), List.of(), false, Set.of()),
                WorkTaskDef.TargetSet.EMPTY,
                new WorkTaskDef.TargetSet(Set.of(id(output)), List.of(), false, Set.of()),
                WorkTaskDef.TargetSet.EMPTY,
                1, WorkTaskDef.Scope.WORKSITE, Conditions.ALWAYS);
    }

    private static WorkstationDef station(String block) {
        return new WorkstationDef(id("test:station"), Set.of(id(block)), List.of(),
                StationType.FURNACE_STATION, 7, 6, List.of(), null, 0, 200, false);
    }

    private static DiscoveredRecipe recipe(String output) {
        ResourceLocation id = id(output);
        return new DiscoveredRecipe(id, StationType.FURNACE_STATION, 1, id, 1, 200,
                false, null, 0, List.of(), false, false, null);
    }

    @Test
    void bakerFurnaceDeclarationDoesNotLeakCookedFish() {
        WorkTaskDef baker = task("minecraft:furnace", "minecraft:bread");
        WorkstationDef furnace = station("minecraft:furnace");

        assertTrue(RecipeOrderCatalogGate.allowedByAny(List.of(baker), furnace,
                recipe("minecraft:bread")));
        assertFalse(RecipeOrderCatalogGate.allowedByAny(List.of(baker), furnace,
                recipe("minecraft:cooked_cod")));
    }

    @Test
    void declarationForAnotherStationCannotAuthorizeThisOne() {
        WorkTaskDef pot = task("farm_and_charm:cooking_pot", "minecraft:cooked_cod");

        assertFalse(RecipeOrderCatalogGate.allowedByAny(List.of(pot), station("minecraft:furnace"),
                recipe("minecraft:cooked_cod")));
    }
}
