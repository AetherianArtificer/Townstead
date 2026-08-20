package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.profession.ProfessionAutoAssign;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.conczin.mca.entity.VillagerEntityMCA;

public final class CookAutoAssignTicker {
    private static final int COOK_ASSIGN_INTERVAL_TICKS = 200;

    private CookAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        ProfessionAutoAssign.tick(villager, WorkTaskTypes.COOK,
                TownsteadConfig.isTownsteadCookEnabled(), COOK_ASSIGN_INTERVAL_TICKS);
    }
}
