package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import net.conczin.mca.entity.VillagerEntityMCA;

public final class VillagerServerTickDispatcher {
    private VillagerServerTickDispatcher() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        CookAutoAssignTicker.tick(villager);
        BaristaAutoAssignTicker.tick(villager);
        CookTradeBackfillTicker.tick(villager);
        BaristaTradeBackfillTicker.tick(villager);
        HungerVillagerTicker.tick(villager);
        if (ThirstBridgeResolver.isActive()) {
            ThirstVillagerTicker.tick(villager);
        }
        FatigueVillagerTicker.tick(villager);
        ProfessionProgressMemoryTicker.tick(villager);
        GuardRestEnforcerTicker.tick(villager);
    }
}
