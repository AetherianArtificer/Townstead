package com.aetherianartificer.townstead.tick;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Every tick, checks if a guard/archer villager's schedule says REST.
 * If so, clears patrol memories but allows reactive combat when recently hurt.
 * Guards during REST seek bed, react to attacks, but don't patrol.
 */
public final class GuardRestEnforcerTicker {

    private static final int RECENT_HURT_TICKS = 200;

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

        // If recently hurt, let them fight — skip enforcement
        if (villager.getLastHurtByMob() != null
                && (villager.tickCount - villager.getLastHurtByMobTimestamp()) < RECENT_HURT_TICKS) {
            return;
        }

        // Only erase patrol memories if no active attack target
        if (brain.getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
    }
}
