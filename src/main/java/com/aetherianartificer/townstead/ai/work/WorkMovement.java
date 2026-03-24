package com.aetherianartificer.townstead.ai.work;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;

import javax.annotation.Nullable;

public final class WorkMovement {
    private WorkMovement() {}

    public static WorkNavigationResult tickMoveToTarget(
            VillagerEntityMCA villager,
            @Nullable WorkTarget target,
            float walkSpeed,
            int closeEnough,
            double arrivalDistanceSq,
            WorkTargetProgress progress,
            WorkTargetFailures failures,
            long gameTime,
            int stuckTickThreshold,
            int maxRetries,
            int blacklistTicks
    ) {
        return tickMoveToTarget(
                villager,
                target == null ? null : target.pos(),
                walkSpeed,
                closeEnough,
                arrivalDistanceSq,
                progress,
                failures,
                gameTime,
                stuckTickThreshold,
                maxRetries,
                blacklistTicks
        );
    }

    public static WorkNavigationResult tickMoveToTarget(
            VillagerEntityMCA villager,
            @Nullable BlockPos targetPos,
            float walkSpeed,
            int closeEnough,
            double arrivalDistanceSq,
            WorkTargetProgress progress,
            WorkTargetFailures failures,
            long gameTime,
            int stuckTickThreshold,
            int maxRetries,
            int blacklistTicks
    ) {
        if (villager == null || targetPos == null) return WorkNavigationResult.NO_TARGET;

        BehaviorUtils.setWalkAndLookTargetMemories(villager, targetPos, walkSpeed, closeEnough);
        double distSq = villager.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5);
        if (distSq <= arrivalDistanceSq) {
            progress.reset();
            failures.clear(targetPos);
            return WorkNavigationResult.ARRIVED;
        }
        if (!progress.record(targetPos, distSq, 0.05d, stuckTickThreshold)) {
            return WorkNavigationResult.MOVING;
        }
        failures.recordFailure(targetPos, gameTime, maxRetries, blacklistTicks);
        progress.reset();
        return WorkNavigationResult.BLOCKED;
    }
}
