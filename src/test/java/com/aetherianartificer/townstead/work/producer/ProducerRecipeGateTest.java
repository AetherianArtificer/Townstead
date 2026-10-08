package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.work.recipe.StationType;

import com.aetherianartificer.townstead.work.station.WorkstationDef;
import com.aetherianartificer.townstead.work.station.Workstations;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The recipe gate, not the declaration. A profession declaring several cook-family tasks scopes
 * each to its own stations; without that, one task's open recipe set vouches for every station in
 * the family and every other task's scoping is dead weight.
 */
class ProducerRecipeGateTest {

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }

    private static WorkstationDef workstation(String defId, String blockId, StationType role) {
        return new WorkstationDef(id(defId), java.util.Set.of(id(blockId)), List.of(), role,
                7, 6, List.of(), null, 0, 200, false);
    }

    /** A cook-family task over the given stations, with an optional recipe allowlist. */
    private static WorkTaskDef task(List<String> stations, List<String> recipes) {
        return new WorkTaskDef(
                id("townstead_work:cook"),
                new WorkTaskDef.TargetSet(
                        stations.stream().map(ProducerRecipeGateTest::id).collect(java.util.stream.Collectors.toSet()),
                        List.of(), false, java.util.Set.of()),
                WorkTaskDef.TargetSet.EMPTY,
                new WorkTaskDef.TargetSet(
                        recipes.stream().map(ProducerRecipeGateTest::id).collect(java.util.stream.Collectors.toSet()),
                        List.of(), false, java.util.Set.of()),
                WorkTaskDef.TargetSet.EMPTY,
                1,
                WorkTaskDef.Scope.WORKSITE,
                com.aetherianartificer.townstead.pheno.condition.Conditions.ALWAYS);
    }

    @BeforeEach
    void registerStations() {
        Workstations.replaceAll(List.of(
                workstation("test:pot", "farmersdelight:cooking_pot", StationType.HOT_STATION),
                workstation("test:furnace", "minecraft:furnace", StationType.FURNACE_STATION)));
    }

    @Test
    void anOpenCookwareTaskDoesNotVouchForTheFurnace() {
        // The shipped shape: cookware wide open, furnace scoped.
        List<WorkTaskDef> tasks = List.of(
                task(List.of("farmersdelight:cooking_pot"), List.of()),
                task(List.of("minecraft:furnace"), List.of("minecraft:cooked_beef")));

        assertTrue(ProducerTaskDeclarations.allowsRecipe(
                        tasks, StationType.FURNACE_STATION, id("minecraft:cooked_beef"), id("minecraft:cooked_beef")),
                "the furnace task admits what it declared");
        assertFalse(ProducerTaskDeclarations.allowsRecipe(
                        tasks, StationType.FURNACE_STATION, id("minecraft:iron_ingot"), id("minecraft:iron_ingot")),
                "the open cookware task must not vouch for smelting ore at a furnace");
    }

    @Test
    void cookwareKeepsItsOpenRecipeSet() {
        List<WorkTaskDef> tasks = List.of(
                task(List.of("farmersdelight:cooking_pot"), List.of()),
                task(List.of("minecraft:furnace"), List.of("minecraft:cooked_beef")));

        assertTrue(ProducerTaskDeclarations.allowsRecipe(
                        tasks, StationType.HOT_STATION, id("farmersdelight:beef_stew"), id("farmersdelight:beef_stew")),
                "scoping the furnace must not narrow what the pot may cook");
    }

    @Test
    void anUngovernedStationKindStaysOpen() {
        // Nothing declares a cutting board here; the station gates already decided access, so the
        // recipe passes rather than dying in an allowlist nobody wrote.
        List<WorkTaskDef> tasks = List.of(
                task(List.of("minecraft:furnace"), List.of("minecraft:cooked_beef")));

        assertTrue(ProducerTaskDeclarations.allowsRecipe(
                tasks, StationType.CUTTING_BOARD, id("farmersdelight:sliced_cake"), id("farmersdelight:cake_slice")));
    }

    @Test
    void aTaskWithNoDeclaredStationsStillGovernsEverything() {
        List<WorkTaskDef> tasks = List.of(
                task(List.of(), List.of("minecraft:cooked_beef")));

        assertTrue(ProducerTaskDeclarations.allowsRecipe(
                tasks, StationType.FURNACE_STATION, id("minecraft:cooked_beef"), id("minecraft:cooked_beef")));
        assertFalse(ProducerTaskDeclarations.allowsRecipe(
                        tasks, StationType.FURNACE_STATION, id("minecraft:iron_ingot"), id("minecraft:iron_ingot")),
                "an undeclared-station task governs every station, so its scope still binds");
    }
}
