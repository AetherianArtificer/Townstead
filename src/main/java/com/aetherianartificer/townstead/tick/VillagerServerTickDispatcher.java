package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;

public final class VillagerServerTickDispatcher {
    private VillagerServerTickDispatcher() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        CookAutoAssignTicker.tick(villager);
        HungerVillagerTicker.tick(villager);
    }
}
