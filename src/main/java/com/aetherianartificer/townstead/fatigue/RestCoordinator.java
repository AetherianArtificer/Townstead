package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;

public final class RestCoordinator {
    private RestCoordinator() {}

    public static RestContext capture(VillagerEntityMCA villager, CompoundTag fatigue, boolean hasValidSleepingBed, boolean guardRole) {
        return new RestContext(
                TownsteadConfig.isVillagerFatigueEnabled(),
                currentScheduleActivity(villager),
                FatigueData.getFatigue(fatigue),
                FatigueData.isCollapsed(fatigue),
                villager.isSleeping(),
                villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isPresent(),
                villager.getLastHurtByMob() != null,
                villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent(),
                hasValidSleepingBed,
                guardRole
        );
    }

    public static RestDecision decide(RestContext context) {
        SleepReason reason = determineReason(context);
        SleepBlockReason blockReason = determineBlockReason(context, reason);

        boolean shouldWake = context.sleeping()
                && (blockReason == SleepBlockReason.ATTACK_TARGET
                || blockReason == SleepBlockReason.INVALID_SLEEPING_BED
                || (!context.isScheduledRest() && !context.isDrowsyOrWorse()));
        boolean shouldSeekBed = !context.sleeping()
                && reason == SleepReason.FATIGUE_REST
                && blockReason == SleepBlockReason.NONE;
        boolean shouldOverride = !context.sleeping()
                && reason == SleepReason.FATIGUE_REST
                && context.scheduleActivity() != Activity.REST
                && blockReason == SleepBlockReason.NONE;
        boolean shouldHoldGuardAtRest = context.guardRole()
                && context.isScheduledRest()
                && !context.sleeping()
                && !context.hasAttackTarget()
                && !context.hurtByMob()
                && !context.isDrowsyOrWorse()
                && !context.hasHome();

        return new RestDecision(reason, blockReason, shouldSeekBed, shouldOverride, shouldWake, shouldHoldGuardAtRest);
    }

    public static void recordDecision(VillagerEntityMCA villager, CompoundTag fatigue, RestDecision decision, BlockPos targetBed) {
        String previousReason = RestDebugData.getRestDebugReasonId(fatigue);
        String previousBlock = RestDebugData.getRestDebugBlockId(fatigue);
        long previousTarget = RestDebugData.getRestDebugTargetBed(fatigue);

        RestDebugData.setRestDebugDecision(fatigue, decision.reason(), decision.blockReason(), targetBed);

        if (!TownsteadConfig.isVillagerSleepDebugEnabled()) return;

        long newTarget = targetBed == null ? Long.MIN_VALUE : targetBed.asLong();
        if (previousReason.equals(decision.reason().id())
                && previousBlock.equals(decision.blockReason().id())
                && previousTarget == newTarget) {
            return;
        }

        Townstead.LOGGER.info(
                "Sleep decision villager={} reason={} block={} target={}",
                villager.getUUID(),
                decision.reason().id(),
                decision.blockReason().id(),
                targetBed == null ? "-" : targetBed
        );
    }

    public static void recordBlockedDecision(VillagerEntityMCA villager, CompoundTag fatigue, SleepReason reason, SleepBlockReason blockReason, BlockPos targetBed) {
        recordDecision(villager, fatigue, new RestDecision(reason, blockReason, false, false, false, false), targetBed);
    }

    static SleepReason determineReason(RestContext context) {
        if (context.collapsed()) return SleepReason.EMERGENCY_COLLAPSE;
        if (context.isDrowsyOrWorse()) return SleepReason.FATIGUE_REST;
        if (context.isScheduledRest()) return SleepReason.SCHEDULED_REST;
        return SleepReason.NONE;
    }

    static SleepBlockReason determineBlockReason(RestContext context, SleepReason reason) {
        if (context.sleeping()) {
            if (context.hasAttackTarget()) return SleepBlockReason.ATTACK_TARGET;
            if (!context.hasValidSleepingBed()) return SleepBlockReason.INVALID_SLEEPING_BED;
            return SleepBlockReason.NONE;
        }
        if (reason == SleepReason.NONE) return SleepBlockReason.NOT_RESTING;
        if (context.collapsed()) return SleepBlockReason.COLLAPSED;
        if (context.hasAttackTarget()) return SleepBlockReason.ATTACK_TARGET;
        if (context.hurtByMob()) return SleepBlockReason.COMBAT_THREAT;
        return SleepBlockReason.NONE;
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }
}
