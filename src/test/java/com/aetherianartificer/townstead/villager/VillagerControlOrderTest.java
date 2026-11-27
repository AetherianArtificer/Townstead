package com.aetherianartificer.townstead.villager;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerControlOrderTest {
    @Test
    void theOrderIsTheOneDecided() {
        assertEquals(List.of(VillagerControl.Owner.DOWNED, VillagerControl.Owner.COLLAPSED, VillagerControl.Owner.REACTION,
                VillagerControl.Owner.RITUAL, VillagerControl.Owner.STORY, VillagerControl.Owner.REST,
                VillagerControl.Owner.LEAVING, VillagerControl.Owner.COMPANION, VillagerControl.Owner.PERSONA_WALK,
                VillagerControl.Owner.HANGOUT, VillagerControl.Owner.WORK), List.of(VillagerControl.Owner.values()));
    }

    @Test
    void onlyMomentaryOwnersPauseInsteadOfEnding() {
        for (VillagerControl.Owner owner : VillagerControl.Owner.values()) {
            boolean momentary = owner == VillagerControl.Owner.REACTION || owner == VillagerControl.Owner.STORY;
            if (momentary) assertTrue(owner.brief(), owner.name());
            else assertFalse(owner.brief(), owner.name());
        }
    }
}
