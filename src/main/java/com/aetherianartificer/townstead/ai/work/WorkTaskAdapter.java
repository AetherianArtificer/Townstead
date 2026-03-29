package com.aetherianartificer.townstead.ai.work;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;

public interface WorkTaskAdapter {
    @Nullable WorkSiteRef activeWorkSite(ServerLevel level, VillagerEntityMCA villager);

    @Nullable WorkTarget activeWorkTarget(ServerLevel level, VillagerEntityMCA villager);

    float navigationWalkSpeed(ServerLevel level, VillagerEntityMCA villager);

    int navigationCloseEnough(ServerLevel level, VillagerEntityMCA villager);

    double navigationArrivalDistanceSq(ServerLevel level, VillagerEntityMCA villager);

    default String navigationState(ServerLevel level, VillagerEntityMCA villager) {
        return "unknown";
    }

    default String navigationBlockedState(ServerLevel level, VillagerEntityMCA villager) {
        return "none";
    }

    default boolean shouldAnnounceBlockedNavigation(ServerLevel level, VillagerEntityMCA villager, @Nullable WorkTarget target) {
        return target == null || !target.isBuildingApproachLike();
    }
}
