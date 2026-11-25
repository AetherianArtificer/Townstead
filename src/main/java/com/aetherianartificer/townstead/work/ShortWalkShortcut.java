package com.aetherianartificer.townstead.work;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Takes the short way to somewhere close, and gets a stalled walker going again. MCA's navigation
 * can plan a long way round to a cell a few steps off (out of a room's door and along the street
 * to reach the far side of the same room), and can stall on a doorstep, standing on its next path
 * node without ever advancing. When the current path to a nearby walk target is far longer than a
 * plain one that also gets there, or the villager hasn't left its cell in a while mid-path, the
 * plain path is followed instead.
 *
 * <p>Never runs as a behavior of its own: the check is the whole of it.</p>
 */
public final class ShortWalkShortcut extends Behavior<VillagerEntityMCA> {

    private static final int CHECK_EVERY = 20;
    /** Only short walks: far targets are where MCA's own routing earns its keep. */
    private static final double NEAR_SQ = 12 * 12;
    /** How much longer the current path must be before it's thrown away. */
    private static final int SLACK = 4;

    /** How long a villager may stand in one cell mid-path before it's taken to be stuck. */
    private static final long STALL_TICKS = 60;
    /** Stuck walkers are helped anywhere a plain path still reaches, within this far. */
    private static final double STALL_FAR_SQ = 48 * 48;

    private long nextCheck = Long.MIN_VALUE;
    private BlockPos lastCell = BlockPos.ZERO;
    private long stillSince;

    public ShortWalkShortcut() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.VALUE_PRESENT), 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long now = level.getGameTime();
        if (nextCheck == Long.MIN_VALUE) nextCheck = now + Math.floorMod(villager.getId(), CHECK_EVERY);
        if (now < nextCheck) return false;
        nextCheck = now + CHECK_EVERY;
        if (!villager.onGround() || villager.isPassenger()) return false;
        WalkTarget walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (walk == null) return false;
        BlockPos goal = walk.getTarget().currentBlockPosition();
        Path current = villager.getNavigation().getPath();
        BlockPos cell = villager.blockPosition();
        if (!cell.equals(lastCell) || current == null || current.isDone()) {
            lastCell = cell.immutable();
            stillSince = now;
        }
        if (current == null || current.isDone()) return false;
        // Only a walk the brain asked for: a seek steers the navigation itself, and the walk target
        // left in memory may be somewhere else entirely.
        if (current.getTarget().distManhattan(goal) > walk.getCloseEnoughDist() + 2) return false;
        double distSq = villager.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
        boolean stalled = now - stillSince >= STALL_TICKS && distSq <= STALL_FAR_SQ;
        if (!stalled && distSq > NEAR_SQ) return false;
        int left = current.getNodeCount() - current.getNextNodeIndex();

        GroundPathNavigation plain = new GroundPathNavigation(villager, level);
        plain.setCanOpenDoors(true);
        plain.setCanPassDoors(true);
        Path shorter = plain.createPath(goal, walk.getCloseEnoughDist());
        if (shorter == null || !shorter.canReach()) return false;
        if (!stalled && shorter.getNodeCount() + SLACK >= left) return false;
        villager.getNavigation().moveTo(shorter, walk.getSpeedModifier());
        stillSince = now;
        return false;
    }
}
