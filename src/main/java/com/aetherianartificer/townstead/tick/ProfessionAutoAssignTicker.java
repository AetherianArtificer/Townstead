package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.profession.ProfessionAutoAssign;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.conczin.mca.entity.VillagerEntityMCA;

/** Runs the task-based auto-assignment policies that Townstead owns. */
public final class ProfessionAutoAssignTicker {
    private static final int ASSIGN_INTERVAL_TICKS = 200;

    private ProfessionAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        ProfessionAutoAssign.tick(villager, WorkTaskTypes.COOK,
                TownsteadConfig.isTownsteadCookEnabled(), ASSIGN_INTERVAL_TICKS);

        // Individual building providers and path data carry their own mod gates. The root
        // profession remains available to future coffee, wine, beer, and tavern packs.
        ProfessionAutoAssign.tick(villager, WorkTaskTypes.BREW, true, ASSIGN_INTERVAL_TICKS);
    }
}
