package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.needs.NeedPace;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/** When "needs can kill" is on, an empty need hurts every four seconds, like a starving player. */
final class NeedHarm {
    private NeedHarm() {}

    private static final int INTERVAL_TICKS = 80;
    private static final Map<VillagerEntityMCA, Long> LAST = new WeakHashMap<>();

    static boolean due(VillagerEntityMCA villager, ServerLevel level) {
        if (!NeedPace.canKill() || NeedPace.inGrace(level.getGameTime())) return false;
        if (com.aetherianartificer.townstead.root.LifeStageProgression.isBabyStage(villager)) return false;
        long now = level.getGameTime();
        Long last = LAST.get(villager);
        if (last != null && now - last < INTERVAL_TICKS) return false;
        LAST.put(villager, now);
        return last != null;
    }
}
