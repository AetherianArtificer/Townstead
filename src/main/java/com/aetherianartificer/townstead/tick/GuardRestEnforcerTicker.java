package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Every tick, checks if a guard/archer villager's schedule says REST.
 * If so, clears all combat-related memories to prevent them from
 * patrolling or engaging enemies while they should be sleeping.
 */
public final class GuardRestEnforcerTicker {

    private GuardRestEnforcerTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        // Only applies to guards/archers
        VillagerProfession prof = villager.getVillagerData().getProfession();
        //? if neoforge {
        boolean isGuard = prof == ProfessionsMCA.GUARD || prof == ProfessionsMCA.ARCHER;
        //?} else {
        /*boolean isGuard = prof == ProfessionsMCA.GUARD.get() || prof == ProfessionsMCA.ARCHER.get();
        *///?}
        if (!isGuard) return;

        // Check if their schedule says REST right now
        Brain<?> brain = villager.getBrain();
        long dayTime = villager.level().getDayTime() % 24000L;
        Activity current = brain.getSchedule().getActivityAt((int) dayTime);
        if (current != Activity.REST) return;

        // Clear all combat-related memories to enforce rest
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
}
