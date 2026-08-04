package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.profession.ProfessionAutoAssign;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import net.conczin.mca.entity.VillagerEntityMCA;

public final class BaristaAutoAssignTicker {
    private static final int BARISTA_ASSIGN_INTERVAL_TICKS = 200;

    private BaristaAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        ProfessionAutoAssign.tick(villager, WorkTaskTypes.BREW,
                TownsteadConfig.isTownsteadCookEnabled() && ModCompat.isLoaded("rusticdelight"),
                BARISTA_ASSIGN_INTERVAL_TICKS);
    }
}
