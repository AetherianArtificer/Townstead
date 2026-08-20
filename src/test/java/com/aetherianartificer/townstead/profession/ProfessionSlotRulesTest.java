package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.JobSiteProvider;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.profession.def.ProgressionTrack;
import com.aetherianartificer.townstead.profession.def.RetrainingPolicy;
import com.aetherianartificer.townstead.profession.def.UnlockModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionSlotRulesTest {

    @AfterEach
    void clearDefs() {
        ProfessionDefs.replaceAll(Map.of());
    }

    private static void registerDef(String id, List<JobSiteProvider> jobSites) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        ProfessionDefs.replaceAll(Map.of(rl, new ProfessionDef(rl, null, null,
                new ProgressionTrack(List.of(0), 0, 0), UnlockModel.EXPERIENTIAL, 1,
                RetrainingPolicy.FREE, List.of(), List.of(), false,
                Conditions.ALWAYS, List.of(), jobSites)));
    }

    @Test
    void defDeclaredBuildingSiteDrivesBuildingSlots() {
        registerDef("somepack:alchemist", List.of(
                new JobSiteProvider.Building(List.of("compat/somepack/lab_l"))));
        assertEquals(ProfessionSlotRules.SlotPolicy.CUSTOM_BUILDING_SLOTS,
                ProfessionSlotRules.classify("somepack:alchemist", false),
                "a building provider in data classifies without any Java rule");
    }

    @Test
    void defDeclaredJobBlockDrivesPoiLimit() {
        registerDef("somepack:scribe", List.of(
                new JobSiteProvider.JobBlock(Set.of(ResourceLocation.tryParse("minecraft:lectern")))));
        assertEquals(ProfessionSlotRules.SlotPolicy.POI_LIMITED,
                ProfessionSlotRules.classify("somepack:scribe", false));
    }

    @Test
    void defDeclaredAlwaysIsUnlimited() {
        registerDef("somepack:wanderer", List.of(new JobSiteProvider.Always()));
        assertEquals(ProfessionSlotRules.SlotPolicy.UNLIMITED,
                ProfessionSlotRules.classify("somepack:wanderer", true),
                "an always provider wins even when a job block is held");
    }
    @Test
    void classifiesUnlimitedRoles() {
        assertEquals(ProfessionSlotRules.SlotPolicy.UNLIMITED,
                ProfessionSlotRules.classify("minecraft:none", false));
        assertEquals(ProfessionSlotRules.SlotPolicy.UNLIMITED,
                ProfessionSlotRules.classify("minecraft:nitwit", false));
        assertEquals(ProfessionSlotRules.SlotPolicy.UNLIMITED,
                ProfessionSlotRules.classify("mca:guard", false));
        assertEquals(ProfessionSlotRules.SlotPolicy.UNLIMITED,
                ProfessionSlotRules.classify("mca:archer", false));
    }

    @Test
    void classifiesTownsteadBuildingSlotRoles() {
        assertEquals(ProfessionSlotRules.SlotPolicy.CUSTOM_BUILDING_SLOTS,
                ProfessionSlotRules.classify("townstead:cook", false));
        assertEquals(ProfessionSlotRules.SlotPolicy.CUSTOM_BUILDING_SLOTS,
                ProfessionSlotRules.classify("townstead:barista", false));
    }

    @Test
    void classifiesPoiBackedRolesByWorkstationPresence() {
        assertEquals(ProfessionSlotRules.SlotPolicy.POI_LIMITED,
                ProfessionSlotRules.classify("minecraft:farmer", true));
        assertEquals(ProfessionSlotRules.SlotPolicy.POI_LIMITED,
                ProfessionSlotRules.classify("mca:miner", true));
        assertEquals(ProfessionSlotRules.SlotPolicy.UNLIMITED,
                ProfessionSlotRules.classify("mca:mercenary", false));
    }
}
