package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.thirst.ThirstWasTakenBridge;
import net.conczin.mca.entity.VillagerEntityMCA;

public final class VillagerServerTickDispatcher {
    private VillagerServerTickDispatcher() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        CookAutoAssignTicker.tick(villager);
        BaristaAutoAssignTicker.tick(villager);
        CookTradeBackfillTicker.tick(villager);
        HungerVillagerTicker.tick(villager);
        if (ThirstWasTakenBridge.INSTANCE.isActive()) {
            ThirstVillagerTicker.tick(villager);
        }
        ProfessionProgressMemoryTicker.tick(villager);
    }
}
