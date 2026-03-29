package com.aetherianartificer.townstead.profession;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProfessionSlotRulesTest {
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
