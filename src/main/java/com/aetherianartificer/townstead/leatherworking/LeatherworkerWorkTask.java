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
        Match match = findActionableJob(level, villager);
        if (debugEnabled() && (level.getGameTime() & 127L) == 0L) {
            // Log roughly once every 6 seconds per leatherworker so the
            // operator can see whether the gate is evaluating jobs at all.
            int jobs = LeatherworkerJobs.all().size();
            Townstead.LOGGER.info("[Leatherworker] gate t={} villager={} jobsRegistered={} match={}",
                    level.getGameTime(), villager.getStringUUID(), jobs,
                    match == null ? "null" : (match.job.getClass().getSimpleName() + "@" + match.plan.anchor()));
        }
        return match != null;
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
        startedTick = gameTime;
        lastPathTick = gameTime;
        executeAfterTick = gameTime + POST_ARRIVAL_PAUSE_TICKS;

        // If the villager is already in arm's reach of the target (e.g.
        // vanilla WorkAtPoi just delivered them to their cauldron POI),
        // skip the path phase so we don't compete with vanilla over
        // WALK_TARGET for a journey that's already complete.
        if (isCloseEnough(villager, claimedPos)) {
            phase = Phase.PROCESS;
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        } else {
            phase = Phase.PATH;
            setWalkTarget(villager, standPos != null ? standPos : claimedPos);
        }
        debug(level, villager, "start job={} anchor={} stand={} phase={}",
                activeJob.getClass().getSimpleName(), claimedPos, standPos, phase);
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

    /** How far below the target we'll drop looking for a floor to stand on. */
    private static final int STAND_DROP_LIMIT = 6;
    /** Horizontal radius around the target's column we'll consider for standing. */
    private static final int STAND_HORIZONTAL_RADIUS = 2;

    /**
     * Find a walkable cell near the target. For wall-mounted blocks like
     * skin racks the rack itself is typically a couple of blocks above
     * floor level, so the cell directly in front of the rack at the rack's
     * Y has no floor under it. Drop down to actual floor level first and
     * then pick the standable cell — preferring the rack's
     * {@code HORIZONTAL_FACING} side (where a player would stand to use it).
     * Falls back to {@code null} when nothing within
     * {@link #STAND_HORIZONTAL_RADIUS} blocks is standable.
     */
    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos target) {
        int floorY = findFloorY(level, target);
        if (floorY == Integer.MIN_VALUE) return null;
        int standY = floorY + 1;

        BlockState state = level.getBlockState(target);
        Direction facing = facingOf(state);
        if (facing != null) {
            // The cell directly in front of the rack at floor level is the
            // canonical "stand here and reach up" spot. Try that first.
            BlockPos preferred = new BlockPos(target.getX() + facing.getStepX(),
                    standY, target.getZ() + facing.getStepZ());
            if (isStandable(level, preferred)) return preferred;
        }

        BlockPos villagerPos = villager.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -STAND_HORIZONTAL_RADIUS; dx <= STAND_HORIZONTAL_RADIUS; dx++) {
            for (int dz = -STAND_HORIZONTAL_RADIUS; dz <= STAND_HORIZONTAL_RADIUS; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos candidate = new BlockPos(target.getX() + dx, standY, target.getZ() + dz);
                if (!isStandable(level, candidate)) continue;
                // Prefer cells aligned with the rack's facing (smaller XZ
                // distance to the rack column), then closer to the villager.
                double rackXz = (double) dx * dx + (double) dz * dz;
                double villagerDs = candidate.distSqr(villagerPos);
                double score = rackXz * 4.0 + villagerDs;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    @Nullable
    private static Direction facingOf(BlockState state) {
        DirectionProperty prop = BlockStateProperties.HORIZONTAL_FACING;
        return state.hasProperty(prop) ? state.getValue(prop) : null;
    }

    /**
     * Drop down from one block below the target until we hit a non-air
     * solid block. Starting at {@code target - 1} avoids returning the
     * target's own Y for wall-mounted blocks (where the target column
     * itself isn't air). Returns the Y of the floor block, or
     * {@link Integer#MIN_VALUE} if nothing was found within
     * {@link #STAND_DROP_LIMIT} blocks.
     */
    private static int findFloorY(ServerLevel level, BlockPos target) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= STAND_DROP_LIMIT; dy++) {
            cursor.set(target.getX(), target.getY() - dy, target.getZ());
            BlockState s = level.getBlockState(cursor);
            if (s.isAir() || s.canBeReplaced()) continue;
            return cursor.getY();
        }
        return Integer.MIN_VALUE;
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
