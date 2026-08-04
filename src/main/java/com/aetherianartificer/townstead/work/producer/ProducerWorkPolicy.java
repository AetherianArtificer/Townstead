package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.profession.BaristaAssignment;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

public final class ProducerWorkPolicy {
    private ProducerWorkPolicy() {}

    public static boolean cookExcludesBeverages(ServerLevel level, VillagerEntityMCA villager) {
        if (!ModCompat.isLoaded("rusticdelight")) return false;
        return BaristaAssignment.hasWorkingBarista(level, villager);
    }
}
