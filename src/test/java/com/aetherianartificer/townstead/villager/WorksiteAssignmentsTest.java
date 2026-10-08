package com.aetherianartificer.townstead.villager;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class WorksiteAssignmentsTest {

    @Test
    void automaticIsTheBackwardCompatibleDefault() {
        WorksiteAssignmentPolicy state = new WorksiteAssignmentPolicy();

        assertTrue(state.automatic());
        assertTrue(state.permitsAdditional(42L));
    }

    @Test
    void manualAllowListAdmitsOnlySelectedStableIds() {
        WorksiteAssignmentPolicy policy = new WorksiteAssignmentPolicy();
        policy.setManual(Set.of(12L, 44L));

        assertEquals(WorksiteAssignmentPolicy.Mode.MANUAL, policy.mode());
        assertTrue(policy.permitsAdditional(12L));
        assertTrue(policy.permitsAdditional(44L));
        assertFalse(policy.permitsAdditional(99L));
    }
}
