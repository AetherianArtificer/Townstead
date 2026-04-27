package com.aetherianartificer.townstead.leatherworking;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.producer.ProducerStationClaims;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Single brain behavior for the vanilla {@code minecraft:leatherworker}
 * profession. Walks the villager to whichever {@link LeatherworkerJob} has
 * actionable work, runs one transition, and ends. The next brain
 * re-evaluation picks the next action (which may be the same job's next
 * stage, or a different job entirely).
 *
 * <p>Compat jobs (e.g. Butchery's Skin Rack) live in their own packages
 * and self-register through {@link LeatherworkerJobs}; this task does not
 * import any mod-specific code directly.
 */
public class LeatherworkerWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 1200;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89;
    private static final double WORK_DISTANCE_SQ = 6.25;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int POST_ARRIVAL_PAUSE_TICKS = 10;

    private enum Phase { PATH, PROCESS }

    @Nullable private LeatherworkerJob activeJob;
    @Nullable private LeatherworkerJob.Plan plan;
    @Nullable private BlockPos claimedPos;
    @Nullable private BlockPos standPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long executeAfterTick;
    private long lastPathTick;

    public LeatherworkerWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.getVillagerData().getProfession() != VillagerProfession.LEATHERWORKER) return false;
        return findActionableJob(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Match match = findActionableJob(level, villager);
        if (match == null) {
            debug(level, villager, "start-abort no job");
            return;
        }
        if (!ProducerStationClaims.tryClaim(level, villager.getUUID(), match.plan.anchor(),
                gameTime + MAX_DURATION + 20L)) {
            debug(level, villager, "start-abort claim failed anchor={}", match.plan.anchor());
            return;
        }
        activeJob = match.job;
        plan = match.plan;
        claimedPos = match.plan.anchor();
        standPos = findStandPos(level, villager, claimedPos);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        executeAfterTick = gameTime + POST_ARRIVAL_PAUSE_TICKS;
        setWalkTarget(villager, standPos != null ? standPos : claimedPos);
        debug(level, villager, "start job={} anchor={} stand={}",
                activeJob.getClass().getSimpleName(), claimedPos, standPos);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeJob == null || plan == null) return false;
        if (gameTime - startedTick > MAX_DURATION) return false;
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) {
            debug(level, villager, "stop path timeout anchor={}", plan.anchor());
            return false;
        }
        if (claimedPos != null
                && ProducerStationClaims.isClaimedByOther(level, villager.getUUID(), claimedPos)) {
            return false;
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeJob == null || plan == null) return;
        BlockPos anchor = plan.anchor();
        villager.getLookControl().setLookAt(
                anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);

        BlockPos walkTarget = standPos != null ? standPos : anchor;
        if (phase == Phase.PATH) {
            double standDsq = villager.distanceToSqr(
                    walkTarget.getX() + 0.5, walkTarget.getY(), walkTarget.getZ() + 0.5);
            if (standDsq <= ARRIVAL_DISTANCE_SQ || isCloseEnough(villager, anchor)) {
                phase = Phase.PROCESS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                executeAfterTick = gameTime + POST_ARRIVAL_PAUSE_TICKS;
            } else {
                setWalkTarget(villager, walkTarget);
            }
            return;
        }

        if (gameTime < executeAfterTick) return;
        if (!isCloseEnough(villager, anchor)) {
            phase = Phase.PATH;
            lastPathTick = gameTime;
            setWalkTarget(villager, walkTarget);
            return;
        }

        try {
            activeJob.execute(level, villager, plan);
        } catch (Throwable t) {
            Townstead.LOGGER.warn("[Leatherworker] job {} threw during execute",
                    activeJob.getClass().getSimpleName(), t);
        }
        // Single action per session: end the task.
        activeJob = null;
        plan = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (claimedPos != null) {
            ProducerStationClaims.release(level, villager.getUUID(), claimedPos);
        }
        activeJob = null;
        plan = null;
        claimedPos = null;
        standPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    @Nullable
    private static Match findActionableJob(ServerLevel level, VillagerEntityMCA villager) {
        for (LeatherworkerJob job : LeatherworkerJobs.all()) {
            if (!job.isAvailable()) continue;
            Optional<LeatherworkerJob.Plan> p = job.findWork(level, villager);
            if (p.isEmpty()) continue;
            BlockPos anchor = p.get().anchor();
            if (ProducerStationClaims.isClaimedByOther(level, villager.getUUID(), anchor)) continue;
            return new Match(job, p.get());
        }
        return null;
    }

    /**
     * Find a walkable cell adjacent to the target so the villager doesn't
     * try to path inside a wall-mounted block. For Butchery skin racks the
     * preferred side is the rack's {@code FACING} (the open face the hide
     * hangs from); for other targets we scan the four cardinals plus
     * diagonals at the same Y. Falls back to {@code null} when no neighbor
     * is standable, in which case callers walk to the anchor itself and
     * the pathfinder figures out what it can.
     */
    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos target) {
        BlockState state = level.getBlockState(target);
        BlockPos preferred = preferredSideFor(state, target);
        if (preferred != null && isStandable(level, preferred)) return preferred;

        BlockPos villagerPos = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        // Iterate the 8 horizontal neighbors at the target's Y. Picking the
        // closest standable cell to the villager keeps backtracking minimal.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = target.offset(dx, 0, dz);
                if (!isStandable(level, candidate)) continue;
                double dsq = candidate.distSqr(villagerPos);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = candidate;
                }
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos preferredSideFor(BlockState state, BlockPos target) {
        // Wall-mounted blocks (skin rack, etc.) carry HORIZONTAL_FACING. The
        // rack's FACING points outward — that's the side the player stands.
        DirectionProperty prop = BlockStateProperties.HORIZONTAL_FACING;
        if (!state.hasProperty(prop)) return null;
        Direction facing = state.getValue(prop);
        return target.relative(facing);
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState body = level.getBlockState(pos);
        if (!body.isAir() && !body.canBeReplaced()) return false;
        BlockState head = level.getBlockState(pos.above());
        if (!head.isAir() && !head.canBeReplaced()) return false;
        BlockState floor = level.getBlockState(pos.below());
        // Floor must be solid enough to stand on. Air or a non-collidable
        // block is treated as "no floor" — pathing onto a ledge is fine,
        // pathing into open space is not.
        return !floor.isAir() && !floor.canBeReplaced();
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos target) {
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), WALK_SPEED, 1));
    }

    private static boolean isCloseEnough(VillagerEntityMCA villager, BlockPos anchor) {
        double dx = villager.getX() - (anchor.getX() + 0.5);
        double dz = villager.getZ() - (anchor.getZ() + 0.5);
        double dy = Math.abs(villager.getY() - anchor.getY());
        return dx * dx + dz * dz <= WORK_DISTANCE_SQ && dy <= 3.0;
    }

    private static boolean debugEnabled() {
        try {
            return TownsteadConfig.DEBUG_VILLAGER_AI.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void debug(ServerLevel level, VillagerEntityMCA villager, String message, Object... args) {
        if (!debugEnabled()) return;
        String formatted = message;
        for (Object arg : args) {
            formatted = formatted.replaceFirst("\\{}",
                    java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        }
        Townstead.LOGGER.info("[Leatherworker] t={} villager={} {}",
                level.getGameTime(), villager.getStringUUID(), formatted);
    }

    private record Match(LeatherworkerJob job, LeatherworkerJob.Plan plan) {}
}
