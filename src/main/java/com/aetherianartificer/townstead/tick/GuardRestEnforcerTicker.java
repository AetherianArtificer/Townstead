package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

/**
 * During REST, prevents guards/archers from aimlessly patrolling.
 * Does NOT interfere with:
 * - Combat (attack targets are never erased)
 * - Bed-seeking (skipped when drowsy+)
 * - Enemy detection (handled by GuardEnemiesSensor, always active)
 */
public final class GuardRestEnforcerTicker {

    private GuardRestEnforcerTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        VillagerProfession prof = villager.getVillagerData().getProfession();
        //? if neoforge {
        boolean isGuard = prof == ProfessionsMCA.GUARD || prof == ProfessionsMCA.ARCHER;
        //?} else {
        /*boolean isGuard = prof == ProfessionsMCA.GUARD.get() || prof == ProfessionsMCA.ARCHER.get();
        *///?}
        if (!isGuard) return;

        Brain<?> brain = villager.getBrain();
        long dayTime = villager.level().getDayTime() % 24000L;
        Activity current = brain.getSchedule().getActivityAt((int) dayTime);
        if (current != Activity.REST) return;

        if (brain.getMemory(MemoryModuleType.ATTACK_TARGET).isPresent()) return;

        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            //? if neoforge {
            CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
            //?} else {
            /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
            *///?}
            if (FatigueData.getFatigue(fatigue) >= FatigueData.DROWSY_THRESHOLD) return;
        }

        if (villager.isSleeping()) return;
        if (brain.getMemory(MemoryModuleType.HOME).isPresent()) return;

        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getNavigation().stop();
    }
}
