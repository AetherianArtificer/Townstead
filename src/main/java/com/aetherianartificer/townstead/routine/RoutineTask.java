package com.aetherianartificer.townstead.routine;

import com.aetherianartificer.townstead.hunger.ConsumableTargetClaims;
import com.aetherianartificer.townstead.hunger.VillagerSearchCadence;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs {@link Routines} in a villager's free time: never at work or rest, asleep, as a child, hurt or panicking. Every
 * so often it looks for the highest-priority routine whose condition holds and whose first step has somewhere to go,
 * then walks it through its steps. A place another villager has claimed is passed over. An arrival action that fails
 * means the place can't be used, and the step looks again elsewhere. Whoever ends a routine riding something they
 * weren't riding at the start is made to stand.
 */
public class RoutineTask extends Behavior<VillagerEntityMCA> {

    private static final String SEARCH = "routine", CLAIM = "routine";
    private static final int SEARCH_EVERY = 400, SEARCH_JITTER = 200;
    // Ticks of walking without getting there before a place is given up.
    private static final int GIVE_UP = 600;
    private static final float WALK_SPEED = 0.6f;

    private enum Plan { TARGET, DONE, STOPPED }

    private @Nullable RoutineDef routine;
    private int step;
    private @Nullable BlockPos target;
    private @Nullable RoutineDef.Option option;
    private boolean arrived, completed, ridingAtStart;
    private long startedAt, walkingSince, progressAt, nextGo;
    private int goes;
    private double startValue, bestValue;
    private final Set<BlockPos> failed = new HashSet<>();
    // Places steps named, and the place the last step ended at.
    private final java.util.Map<String, BlockPos> named = new java.util.HashMap<>();
    private @Nullable BlockPos lastPlace;

    public RoutineTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), 24000);
    }

    private static boolean freeTime(VillagerEntityMCA villager) {
        Activity now = villager.getBrain().getActiveNonCoreActivity().orElse(Activity.IDLE);
        return now != Activity.WORK && now != Activity.REST;
    }

    private static boolean able(VillagerEntityMCA villager) {
        return !villager.isBaby() && !villager.isSleeping() && villager.getLastHurtByMob() == null
                && !villager.getVillagerBrain().isPanicking() && freeTime(villager);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (Routines.all().isEmpty() || !able(villager)) return false;
        if (!VillagerSearchCadence.isDue(level, villager, SEARCH)) return false;
        VillagerSearchCadence.schedule(level, villager, SEARCH, SEARCH_EVERY, SEARCH_JITTER);
        for (RoutineDef def : Routines.all()) {
            if (def.condition() != null && !def.condition().test(new ConditionContext(villager))) continue;
            reset();
            routine = def;
            if (firstTarget(level, villager)) return true;
        }
        reset();
        return false;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (routine == null) return;
        startedAt = gameTime;
        ridingAtStart = villager.isPassenger();
        startValue = bestValue = progress(villager);
        progressAt = gameTime;
        run(routine.start(), villager, target);
        beginWalk(villager, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (routine == null || target == null || option == null) return;
        if (routine.claim()) ConsumableTargetClaims.tryClaimPos(level, villager.getUUID(), CLAIM, target, gameTime + 200);
        if (!arrived) {
            if (there(villager)) {
                arrived = true;
                progressAt = gameTime;
                villager.getNavigation().stop();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                RoutineDef.Step current = routine.steps().get(step);
                boolean usable = run(option.onArrive(), villager, target) && run(current.onArrive(), villager, target);
                if (!usable) lookElsewhere(level, villager, gameTime);
                else nextGo = gameTime;
            } else if (gameTime - walkingSince > GIVE_UP) {
                lookElsewhere(level, villager, gameTime);
            } else {
                walk(villager);
            }
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
        if (option.inFluid()) villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        RoutineDef.Stay stay = option.stay();
        if (stay.action() != null && gameTime >= nextGo && (stay.times() == 0 || goes < stay.times())) {
            run(stay.action(), villager, target);
            goes++;
            nextGo = gameTime + stay.every();
        }
        if (routine.progress() != null) {
            double now = progress(villager);
            double gained = routine.progress().down() ? bestValue - now : now - bestValue;
            if (gained > routine.progress().by()) {
                bestValue = now;
                progressAt = gameTime;
            }
        }
        boolean done = stay.endsAtOnce()
                || (stay.until() != null && stay.until().test(new ConditionContext(villager).at(target)))
                || (stay.times() > 0 && goes >= stay.times() && gameTime >= nextGo)
                || (stay.stalled() > 0 && routine.progress() != null && gameTime - progressAt >= stay.stalled());
        if (done) nextStep(level, villager, gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return routine != null && target != null && able(villager) && gameTime - startedAt < routine.maxTicks();
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        release(level, villager);
        BlockPos endAt = target != null ? target : lastPlace;
        if (villager.isPassenger() && !ridingAtStart) villager.stopRiding();
        if (routine != null && routine.finish() != null) {
            boolean earned;
            if (routine.progress() != null) {
                double now = progress(villager);
                double gained = routine.progress().down() ? startValue - now : now - startValue;
                earned = gained >= routine.finish().minProgress();
            } else {
                earned = completed;
            }
            if (earned) run(routine.finish().action(), villager, null);
        }
        // However it ended: what has to be put right, such as clothes back on.
        if (routine != null) run(routine.end(), villager, endAt);
        reset();
    }

    // ---- Steps.

    /** Plans the first step without running any step's {@code else} actions, so looking costs nothing. */
    private boolean firstTarget(ServerLevel level, VillagerEntityMCA villager) {
        for (step = 0; step < routine.steps().size(); step++) {
            RoutineDef.Step current = routine.steps().get(step);
            if (current.when() != null && !current.when().test(atLastPlace(villager))) continue;
            if (find(level, villager, current)) return true;
            if (current.orElse() != RoutineDef.Else.SKIP) return false;
        }
        return false;
    }

    /** Ends the current step and plans the next; the routine ends when there is none or a step finds nowhere to go. */
    private void nextStep(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        release(level, villager);
        RoutineDef.Step current = routine.steps().get(step);
        if (current.name() != null && target != null) named.put(current.name(), target);
        lastPlace = target;
        if (current.repeat()) {
            // This place is done: on to the next one this step finds, if any.
            failed.add(target);
            if (find(level, villager, current)) {
                beginWalk(villager, gameTime);
                return;
            }
        }
        step++;
        Plan plan = plan(level, villager);
        if (plan == Plan.TARGET) beginWalk(villager, gameTime);
        else {
            completed = plan == Plan.DONE;
            target = null;
        }
    }

    /** The place at this step was no good: try another for the same step. */
    private void lookElsewhere(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target != null) failed.add(target);
        release(level, villager);
        Plan plan = plan(level, villager);
        if (plan == Plan.TARGET) beginWalk(villager, gameTime);
        else {
            completed = plan == Plan.DONE;
            target = null;
        }
    }

    private Plan plan(ServerLevel level, VillagerEntityMCA villager) {
        for (; step < routine.steps().size(); step++) {
            RoutineDef.Step current = routine.steps().get(step);
            if (current.when() != null && !current.when().test(atLastPlace(villager))) continue;
            if (find(level, villager, current)) return Plan.TARGET;
            switch (current.orElse()) {
                case STOP -> {
                    return Plan.STOPPED;
                }
                case SKIP -> {
                }
                case ACTIONS -> run(current.elseAction(), villager, null);
            }
        }
        return Plan.DONE;
    }

    /** The best place for a step: its worth, less half a point a block away, less its penalty. Claims it. */
    private boolean find(ServerLevel level, VillagerEntityMCA villager, RoutineDef.Step current) {
        BlockPos from = villager.blockPosition();
        BlockPos best = null;
        RoutineDef.Option bestOption = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (RoutineDef.Option candidate : current.options()) {
            if (candidate.named() != null) {
                // An earlier step's place: that one block, if the step got there.
                BlockPos pos = named.get(candidate.named());
                if (pos == null || failed.contains(pos)) continue;
                ConditionContext there = new ConditionContext(villager).at(pos);
                if (candidate.usable() != null && !candidate.usable().test(there)) continue;
                double score = candidate.worth() - Math.sqrt(pos.distSqr(from)) * 0.5
                        - (candidate.penalty() != null ? candidate.penalty().get(SelectorContext.of(there)) : 0);
                if (score <= bestScore) continue;
                if (routine.claim() && ConsumableTargetClaims.isClaimedByOtherPos(level, villager.getUUID(), CLAIM, pos)) continue;
                best = pos;
                bestOption = candidate;
                bestScore = score;
                continue;
            }
            for (AABB box : candidate.within().regions(villager, villager, null)) {
                for (BlockPos pos : blocksIn(box)) {
                    if (!level.isLoaded(pos) || failed.contains(pos) || !candidate.block().test(level, pos)) continue;
                    double score = candidate.worth() - Math.sqrt(pos.distSqr(from)) * 0.5;
                    if (score <= bestScore) continue;
                    ConditionContext there = new ConditionContext(villager).at(pos);
                    if (candidate.usable() != null && !candidate.usable().test(there)) continue;
                    if (candidate.penalty() != null) score -= candidate.penalty().get(SelectorContext.of(there));
                    if (score <= bestScore) continue;
                    if (routine.claim() && ConsumableTargetClaims.isClaimedByOtherPos(level, villager.getUUID(), CLAIM, pos)) continue;
                    best = pos.immutable();
                    bestOption = candidate;
                    bestScore = score;
                }
            }
        }
        if (best == null) return false;
        if (routine.claim() && !ConsumableTargetClaims.tryClaimPos(level, villager.getUUID(), CLAIM, best, level.getGameTime() + 200)) return false;
        target = best;
        option = bestOption;
        return true;
    }

    /** The blocks of a region to search, at most 16 blocks out from its middle (8 up and down), as a seek searches. */
    private static Iterable<BlockPos> blocksIn(AABB box) {
        Vec3 c = box.getCenter();
        int rx = (int) Math.min(Math.ceil(box.getXsize() / 2), 16), ry = (int) Math.min(Math.ceil(box.getYsize() / 2), 8),
                rz = (int) Math.min(Math.ceil(box.getZsize() / 2), 16);
        BlockPos center = BlockPos.containing(c);
        return BlockPos.betweenClosed(center.offset(-rx, -ry, -rz), center.offset(rx, ry, rz));
    }

    // ---- Walking and arriving.

    private void beginWalk(VillagerEntityMCA villager, long gameTime) {
        arrived = false;
        goes = 0;
        walkingSince = gameTime;
        walk(villager);
    }

    private void walk(VillagerEntityMCA villager) {
        if (target == null) return;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, target, WALK_SPEED, 1);
    }

    private boolean there(VillagerEntityMCA villager) {
        double reach = option.reach();
        if (villager.distanceToSqr(Vec3.atCenterOf(target)) > reach * reach) return false;
        return !option.inFluid() || villager.isInWater() || !villager.level().getFluidState(villager.blockPosition()).isEmpty();
    }

    // ---- Helpers.

    /** Runs actions on the villager, about {@code at}. Whether they succeeded; no actions count as success. */
    private static boolean run(@Nullable Action action, VillagerEntityMCA villager, @Nullable BlockPos at) {
        if (action == null) return true;
        ActionContext ctx = new ActionContext(villager).withFocusBlock(at);
        action.run(ctx);
        return ctx.succeeded();
    }

    /** A step's {@code when}, asked as if the villager stood where the last step ended, so it can read that place. */
    private ConditionContext atLastPlace(VillagerEntityMCA villager) {
        return lastPlace == null ? new ConditionContext(villager) : new ConditionContext(villager).at(lastPlace);
    }

    private double progress(VillagerEntityMCA villager) {
        if (routine == null || routine.progress() == null) return 0;
        return routine.progress().value().get(SelectorContext.of(new ConditionContext(villager)));
    }

    private void release(ServerLevel level, VillagerEntityMCA villager) {
        if (target != null && routine != null && routine.claim()) {
            ConsumableTargetClaims.releasePos(level, villager.getUUID(), CLAIM, target);
        }
    }

    private void reset() {
        routine = null;
        step = 0;
        target = null;
        option = null;
        arrived = completed = false;
        goes = 0;
        failed.clear();
        named.clear();
        lastPlace = null;
    }
}
