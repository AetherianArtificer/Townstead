package com.aetherianartificer.townstead.compat.butchery;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.VillagerData;

/**
 * Bridges {@link com.aetherianartificer.townstead.hunger.ButcherProgressData}
 * tiers onto vanilla's {@link VillagerData#getLevel()} so butcher XP earned
 * through slaughter and carcass work actually unlocks the higher-tier trades
 * registered by {@link ButcherTradesCompat}.
 *
 * <p>Calls MCA's {@code VillagerEntityMCA.customLevelUp()} which bumps the
 * level and regenerates trade offers for the new tier. Never demotes: a
 * villager whose vanilla trade volume already exceeds our tier keeps their
 * higher level.
 */
public final class ButcherTradeLevelSync {
    private static final int MAX_LEVEL = 5;

    private ButcherTradeLevelSync() {}

    /** Raise the villager's trade level to match {@code targetTier} if it lags behind. */
    public static void syncToTier(VillagerEntityMCA villager, int targetTier) {
        if (villager == null) return;
        int target = Math.min(MAX_LEVEL, Math.max(1, targetTier));
        int current = villager.getVillagerData().getLevel();
        if (target <= current) return;
        int steps = target - current;
        for (int i = 0; i < steps; i++) {
            if (villager.getVillagerData().getLevel() >= MAX_LEVEL) break;
            villager.customLevelUp();
        }
    }
}
