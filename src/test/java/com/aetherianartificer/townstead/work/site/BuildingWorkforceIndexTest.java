package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
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
        ProfessionDefs.replaceAll(Map.of());
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

    @Test
    void pathSpecificProfessionReferenceDoesNotAdmitEveryRootWorker() {
        ResourceLocation externalCook = ResourceLocation.tryParse("somepack:cook");
        ResourceLocation externalChef = ResourceLocation.tryParse("somepack:chef");
        ProfessionDef cook = new ProfessionDef(COOK, null, null,
                new ProgressionTrack(List.of(0), 0, 0), UnlockModel.EXPERIENTIAL, 1,
                RetrainingPolicy.FREE, List.of(), false, Conditions.ALWAYS,
                List.of(), List.of());
        ProfessionDefs.replaceAll(Map.of(COOK, cook), Map.of(
                externalCook, new ProfessionDefs.Resolution(COOK, null),
                externalChef, new ProfessionDefs.Resolution(COOK, "chef")));
        BuildingWorkforceIndex.replaceAll(Map.of("restaurant", List.of(externalChef)));

        assertTrue(BuildingWorkforceIndex.accepts("restaurant", externalChef));
        assertFalse(BuildingWorkforceIndex.accepts("restaurant", COOK));
        assertFalse(BuildingWorkforceIndex.accepts("restaurant", externalCook));
    }
}
