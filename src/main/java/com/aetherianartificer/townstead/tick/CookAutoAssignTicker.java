package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

public final class CookAutoAssignTicker {
    private static final int COOK_ASSIGN_INTERVAL_TICKS = 200;

    private CookAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.tickCount % COOK_ASSIGN_INTERVAL_TICKS != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || !villager.isAlive() || villager.isSleeping()) return;
        if (currentActivity(villager) != Activity.WORK) return;

        VillagerData data = villager.getVillagerData();
        if (data.getProfession() != VillagerProfession.NONE) return;

        VillagerProfession cookProfession = FarmersDelightCookAssignment.resolveAssignableCookProfession();
        if (cookProfession == null) return;
        if (!FarmersDelightCookAssignment.hasAvailableCookSlot(level, villager)) return;

        villager.setVillagerData(data.setProfession(cookProfession));
        villager.refreshBrain(level);
    }

    private static Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }
}
