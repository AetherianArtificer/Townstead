package com.aetherianartificer.townstead.ai.work.producer;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkMovement;
import com.aetherianartificer.townstead.ai.work.WorkNavigationResult;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.WorkTarget;
import com.aetherianartificer.townstead.ai.work.WorkTaskAdapter;
import com.aetherianartificer.townstead.ai.work.WorkTargetFailures;
import com.aetherianartificer.townstead.ai.work.WorkTargetProgress;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public abstract class ProducerWorkTask extends Behavior<VillagerEntityMCA> implements WorkTaskAdapter {

    protected static final int MAX_DURATION = 1200;
    protected static final int CLOSE_ENOUGH = 0;
    protected static final int BUILDING_CLOSE_ENOUGH = 2;
    protected static final double ARRIVAL_DISTANCE_SQ = 0.36d;
    protected static final double NEAR_STATION_DISTANCE_SQ = 9.0d;
    protected static final float WALK_SPEED = 0.52f;
    protected static final int IDLE_BACKOFF = 80;
    protected static final int REQUEST_RANGE = 24;
    protected static final long STAND_REACQUIRE_INTERVAL_TICKS = 60L;
    protected static final long RECIPE_REPEAT_COOLDOWN_TICKS = 200L;
    protected static final long ABANDONED_STATION_COOLDOWN_TICKS = 100L;
    protected static final int COLLECT_WAIT_MAX_TICKS = 40;
    protected static final long STATION_SESSION_LEASE_TICKS = MAX_DURATION + 40L;
    protected static final int OPPORTUNISTIC_SWEEP_INTERVAL = 10;
    protected static final int MAX_RECIPE_ATTEMPTS = 3;
    protected static final long WORKSITE_TARGET_RETRY_COOLDOWN_TICKS = 60L;
    protected static final int WORKSITE_MAX_RETRIES = 2;
    protected static final int DEFAULT_STATE_TIMEOUT_TICKS = 100;
    protected static final int GATHER_STATE_TIMEOUT_TICKS = 140;
    protected static final int PRODUCE_STATE_TIMEOUT_TICKS = 160;
    protected static final int COLLECT_STATE_TIMEOUT_TICKS = 80;

    public enum ProducerState {
        PATH_TO_WORKSITE, PATH_TO_STATION, RECONCILE_STATION,
        SELECT_RECIPE, GATHER, PRODUCE, COLLECT, COLLECT_WAIT
    }

    public record GatherResult(boolean success, @Nullable String detail) {
        public static GatherResult ok() { return new GatherResult(true, null); }
        public static GatherResult fail(@Nullable String detail) { return new GatherResult(false, detail); }
    }

    public record CollectResult(boolean collected, boolean shouldWait) {
        public static CollectResult none() { return new CollectResult(false, false); }
        public static CollectResult ofCollected() { return new CollectResult(true, false); }
        public static CollectResult waiting(boolean alreadyCollected) { return new CollectResult(alreadyCollected, true); }
    }

    public record ProducerStationSelection(
            BlockPos stationPos,
            BlockPos standPos,
            @Nullable ProducerRecipe recipe
    ) {}

    protected ProducerState state = ProducerState.PATH_TO_WORKSITE;
    protected long stateEnteredTick;
    protected @Nullable BlockPos stationAnchor;
    protected @Nullable BlockPos standPos;
    protected @Nullable ProducerRecipe activeRecipe;
    protected ItemStack pendingOutput = ItemStack.EMPTY;
    protected long produceDoneTick;
    protected long nextStandReacquireTick;
    protected long nextDebugTick;
    protected long nextRequestTick;
    protected long idleUntilTick;
    protected int recipeAttempts;
    protected @Nullable BlockPos currentWorksiteTarget;
    protected String currentWorksiteTargetKind = "stand";
    protected ProducerBlockedReason blocked = ProducerBlockedReason.NONE;

    protected final Map<ResourceLocation, Integer> stagedInputs = new HashMap<>();
    protected final Map<ResourceLocation, Long> recipeCooldownUntil = new HashMap<>();
    protected final Map<Long, Long> abandonedUntilByStation = new HashMap<>();
    protected final WorkTargetProgress worksiteTargetProgress = new WorkTargetProgress();
    protected final WorkTargetFailures worksiteTargetFailures = new WorkTargetFailures();

    protected ProducerWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    // ── Abstract: identity / guards ──

    protected abstract boolean isTaskEnabled();

    protected abstract boolean isEligibleVillager(ServerLevel level, VillagerEntityMCA villager);

    // ── Abstract: worksite ──

    protected abstract @Nullable WorkSiteRef resolveWorksite(ServerLevel level, VillagerEntityMCA villager);

    protected abstract boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager);

    protected abstract @Nullable BlockPos resolveWorksiteTarget(
            ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteRef site);

    protected abstract BlockPos worksiteReference(VillagerEntityMCA villager);

    // ── Abstract: station acquisition ──

    protected abstract @Nullable ProducerStationSelection selectStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract void claimStation(ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract void releaseStationClaim(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos);

    // ── Abstract: reconcile ──

    protected abstract ProducerStationState classifyStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract boolean cleanupForeignStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    // ── Abstract: recipe / gather / produce / collect ──

    protected abstract @Nullable ProducerRecipe pickRecipe(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract GatherResult gatherInputs(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract void rollbackGather(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract boolean beginProduce(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract boolean isProduceDone(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract CollectResult collectFromStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    protected abstract void storeOutputs(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    // ── Abstract: XP ──

    protected abstract void awardProductionXp(
            ServerLevel level, VillagerEntityMCA villager, long gameTime);

    // ── Optional hooks (default no-op) ──

    protected void onProduceTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected boolean mustWaitBeyondCollectTimeout(ServerLevel level, VillagerEntityMCA villager) { return false; }

    protected void onSessionRefresh(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void onSessionRelease(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {}

    protected void onOpportunisticSweep(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void playGatherSound(ServerLevel level, VillagerEntityMCA villager) {}

    protected void announceBlocked(
            ServerLevel level, VillagerEntityMCA villager, long gameTime,
            ProducerBlockedReason reason, @Nullable String detail) {}

    protected void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void debugChat(ServerLevel level, VillagerEntityMCA villager, String message) {}

    protected double stationRotationProbability() { return 0.5d; }

    protected int stateTimeoutTicks(ProducerState state) {
        return switch (state) {
            case GATHER -> GATHER_STATE_TIMEOUT_TICKS;
            case PRODUCE -> PRODUCE_STATE_TIMEOUT_TICKS;
            case COLLECT, COLLECT_WAIT -> COLLECT_STATE_TIMEOUT_TICKS;
            default -> DEFAULT_STATE_TIMEOUT_TICKS;
        };
    }

    // ── Behavior lifecycle ──

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!isTaskEnabled()) return false;
        if (isFatigueGated(villager)) return false;
        if (!isEligibleVillager(level, villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return currentActivity(villager) == Activity.WORK;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isEligibleVillager(level, villager)) return;
        blocked = ProducerBlockedReason.NONE;
        state = ProducerState.PATH_TO_WORKSITE;
        stateEnteredTick = gameTime;
        recipeAttempts = 0;
        abandonedUntilByStation.clear();
        resetWorksiteTargeting();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isTaskEnabled()) return false;
        if (!isEligibleVillager(level, villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return currentActivity(villager) == Activity.WORK;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        onStop(level, villager, gameTime);
        releaseStationClaim(level, villager, stationAnchor);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        clearTransientState();
        state = ProducerState.PATH_TO_WORKSITE;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isEligibleVillager(level, villager)) {
            clearAll(level, villager, gameTime);
            return;
        }
        if (gameTime < idleUntilTick) return;

        debugTick(level, villager, gameTime);

        int timeout = stateTimeoutTicks(state);
        if (gameTime - stateEnteredTick > timeout
                && state != ProducerState.PATH_TO_WORKSITE
                && state != ProducerState.PATH_TO_STATION
                && state != ProducerState.RECONCILE_STATION
                && state != ProducerState.PRODUCE
                && state != ProducerState.COLLECT_WAIT) {
            debugChat(level, villager, "STATE:timeout in " + state.name() + ", resetting");
            transitionToNavigationState(level, villager, gameTime);
            releaseStationClaim(level, villager, stationAnchor);
            onSessionRelease(level, villager, stationAnchor, gameTime);
            stationAnchor = null;
            standPos = null;
            activeRecipe = null;
            stagedInputs.clear();
            recipeAttempts = 0;
        }

        if (gameTime % OPPORTUNISTIC_SWEEP_INTERVAL == 0) {
            onOpportunisticSweep(level, villager, gameTime);
        }

        switch (state) {
            case PATH_TO_WORKSITE -> tickPathToWorksite(level, villager, gameTime);
            case PATH_TO_STATION -> tickPathToStation(level, villager, gameTime);
            case RECONCILE_STATION -> tickReconcileStation(level, villager, gameTime);
            case SELECT_RECIPE -> tickSelectRecipe(level, villager, gameTime);
            case GATHER -> tickGather(level, villager, gameTime);
            case PRODUCE -> tickProduce(level, villager, gameTime);
            case COLLECT -> tickCollect(level, villager, gameTime);
            case COLLECT_WAIT -> tickCollectWait(level, villager, gameTime);
        }
    }

    // ── State: PATH_TO_WORKSITE ──

    private void tickPathToWorksite(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        releaseStationClaim(level, villager, stationAnchor);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        stationAnchor = null;
        standPos = null;
        activeRecipe = null;
        stagedInputs.clear();

        WorkSiteRef site = resolveWorksite(level, villager);
        if (site == null) {
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_WORKSITE, null);
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        if (isVillagerAtWorksite(level, villager)) {
            blocked = ProducerBlockedReason.NONE;
            resetWorksiteTargeting();
            transition(ProducerState.PATH_TO_STATION, gameTime);
            return;
        }

        BlockPos target = resolveWorksiteTarget(level, villager, gameTime, site);
        if (target == null) {
            setBlocked(level, villager, gameTime, ProducerBlockedReason.UNREACHABLE, null);
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }
        currentWorksiteTarget = target;

        WorkNavigationResult move = WorkMovement.tickMoveToTarget(
                villager, target, WALK_SPEED, BUILDING_CLOSE_ENOUGH, ARRIVAL_DISTANCE_SQ,
                worksiteTargetProgress, worksiteTargetFailures,
                gameTime, stateTimeoutTicks(state),
                WORKSITE_MAX_RETRIES, (int) WORKSITE_TARGET_RETRY_COOLDOWN_TICKS);
        switch (move) {
            case ARRIVED -> {
                blocked = ProducerBlockedReason.NONE;
                currentWorksiteTarget = null;
                if (isVillagerAtWorksite(level, villager)) {
                    transition(ProducerState.PATH_TO_STATION, gameTime);
                }
            }
            case MOVING -> blocked = ProducerBlockedReason.NONE;
            case BLOCKED -> currentWorksiteTarget = null;
            case NO_TARGET -> {
                currentWorksiteTarget = null;
                setBlocked(level, villager, gameTime, ProducerBlockedReason.UNREACHABLE, null);
            }
        }
    }

    // ── State: PATH_TO_STATION ──

    private void tickPathToStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isVillagerAtWorksite(level, villager)) {
            transition(ProducerState.PATH_TO_WORKSITE, gameTime);
            return;
        }

        ProducerStationSelection selection = selectStation(level, villager, gameTime);
        if (selection == null) {
            debugChat(level, villager, "ACQUIRE:no usable station/recipe pair, resetting");
            recipeAttempts = 0;
            idleUntilTick = gameTime + IDLE_BACKOFF;
            return;
        }

        stationAnchor = selection.stationPos();
        standPos = selection.standPos();
        activeRecipe = selection.recipe();

        claimStation(level, villager, gameTime);

        BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
        transition(ProducerState.RECONCILE_STATION, gameTime);
    }

    // ── State: RECONCILE_STATION ──

    private void tickReconcileStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (stationAnchor == null) {
            transition(ProducerState.PATH_TO_STATION, gameTime);
            return;
        }

        ProducerStationState stationState = classifyStation(level, villager, gameTime);
        switch (stationState) {
            case EMPTY_READY -> transition(activeRecipe != null ? ProducerState.GATHER : ProducerState.SELECT_RECIPE, gameTime);
            case OWNED_STAGED, COMPATIBLE_PARTIAL -> {
                if (activeRecipe != null) {
                    produceDoneTick = Math.max(gameTime + 10L, produceDoneTick);
                    onSessionRefresh(level, villager, gameTime);
                    transition(ProducerState.PRODUCE, gameTime);
                } else {
                    transition(ProducerState.SELECT_RECIPE, gameTime);
                }
            }
            case FINISHED_OUTPUT -> transition(ProducerState.COLLECT, gameTime);
            case FOREIGN_CONTENTS -> {
                boolean cleaned = cleanupForeignStation(level, villager, gameTime);
                if (cleaned) {
                    transition(ProducerState.SELECT_RECIPE, gameTime);
                } else {
                    debugChat(level, villager, "RECONCILE:foreign contents persisted, rotating station");
                    abandonCurrentStation(level, villager, gameTime, true);
                }
            }
            case BLOCKED -> abandonCurrentStation(level, villager, gameTime, true);
        }
    }

    // ── State: SELECT_RECIPE ──

    private void tickSelectRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (stationAnchor == null) {
            transition(ProducerState.PATH_TO_STATION, gameTime);
            return;
        }

        ProducerRecipe recipe = pickRecipe(level, villager, gameTime);
        if (recipe == null) {
            debugChat(level, villager, "SELECT:no recipe, rotating");
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_RECIPE, null);
            abandonCurrentStation(level, villager, gameTime, true);
            return;
        }

        activeRecipe = recipe;
        recipeAttempts = 0;
        transition(ProducerState.GATHER, gameTime);
    }

    // ── State: GATHER ──

    private void tickGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (activeRecipe == null) {
            transition(ProducerState.SELECT_RECIPE, gameTime);
            return;
        }

        GatherResult result = gatherInputs(level, villager, gameTime);
        if (!result.success()) {
            debugChat(level, villager, "GATHER:failed for " + activeRecipe.output()
                    + (result.detail() == null ? "" : " (" + result.detail() + ")"));
            rollbackGather(level, villager, gameTime);
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_INGREDIENTS, result.detail());
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
            activeRecipe = null;
            recipeAttempts++;
            if (recipeAttempts >= MAX_RECIPE_ATTEMPTS) {
                debugChat(level, villager, "GATHER:max attempts, rotating station");
                idleUntilTick = gameTime + IDLE_BACKOFF;
                abandonCurrentStation(level, villager, gameTime, true);
            } else {
                transition(ProducerState.SELECT_RECIPE, gameTime);
            }
            return;
        }

        if (!beginProduce(level, villager, gameTime)) {
            debugChat(level, villager, "BEGIN_PRODUCE:failed, rotating station");
            if (activeRecipe != null) {
                recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
            }
            abandonCurrentStation(level, villager, gameTime, true);
            return;
        }

        onSessionRefresh(level, villager, gameTime);
        playGatherSound(level, villager);
        transition(ProducerState.PRODUCE, gameTime);
    }

    // ── State: PRODUCE ──

    private void tickProduce(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor != null && standPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        }
        if (gameTime % 30 == 0) {
            villager.swing(villager.getDominantHand());
        }

        onProduceTick(level, villager, gameTime);

        if (gameTime < produceDoneTick) return;
        if (!isProduceDone(level, villager, gameTime)) {
            onSessionRefresh(level, villager, gameTime);
            return;
        }

        debugChat(level, villager, "PRODUCE:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        transition(ProducerState.COLLECT, gameTime);
    }

    // ── State: COLLECT ──

    private void tickCollect(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        CollectResult result = collectFromStation(level, villager, gameTime);
        if (result.shouldWait()) {
            transition(ProducerState.COLLECT_WAIT, gameTime);
            return;
        }
        if (!result.collected() && mustWaitBeyondCollectTimeout(level, villager)) {
            transition(ProducerState.COLLECT_WAIT, gameTime);
            return;
        }
        finishCollect(level, villager, gameTime);
    }

    // ── State: COLLECT_WAIT ──

    private void tickCollectWait(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(ProducerState.RECONCILE_STATION, gameTime);
            return;
        }
        if (stationAnchor != null && standPos != null) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        }

        CollectResult result = collectFromStation(level, villager, gameTime);
        long elapsed = gameTime - stateEnteredTick;
        if (result.collected() || elapsed >= COLLECT_WAIT_MAX_TICKS) {
            if (!result.collected() && mustWaitBeyondCollectTimeout(level, villager) && activeRecipe != null) {
                if (elapsed == COLLECT_WAIT_MAX_TICKS) {
                    debugChat(level, villager, "COLLECT_WAIT:still waiting for output " + activeRecipe.output());
                }
                return;
            }
            finishCollect(level, villager, gameTime);
        }
    }

    // ── finishCollect ──

    private void finishCollect(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        storeOutputs(level, villager, gameTime);
        pendingOutput = ItemStack.EMPTY;
        awardProductionXp(level, villager, gameTime);

        if (activeRecipe != null) {
            recipeCooldownUntil.put(activeRecipe.output(), gameTime + RECIPE_REPEAT_COOLDOWN_TICKS);
        }

        debugChat(level, villager, "COLLECT:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        activeRecipe = null;
        stagedInputs.clear();
        onSessionRelease(level, villager, stationAnchor, gameTime);

        if (ThreadLocalRandom.current().nextDouble() < stationRotationProbability()) {
            releaseStationClaim(level, villager, stationAnchor);
            transitionToNavigationState(level, villager, gameTime);
        } else {
            claimStation(level, villager, gameTime);
            transition(ProducerState.SELECT_RECIPE, gameTime);
        }
    }

    // ── Navigation helpers ──

    protected final boolean ensureNearStation(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null || standPos == null) {
            transitionToNavigationState(level, villager, gameTime);
            return false;
        }
        if (!isVillagerAtWorksite(level, villager)) {
            transitionToNavigationState(level, villager, gameTime);
            return false;
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, standPos, WALK_SPEED, CLOSE_ENOUGH);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(stationAnchor));
        double distSq = villager.distanceToSqr(standPos.getX() + 0.5, standPos.getY() + 0.5, standPos.getZ() + 0.5);
        double anchorDistSq = villager.distanceToSqr(
                stationAnchor.getX() + 0.5, stationAnchor.getY() + 0.5, stationAnchor.getZ() + 0.5);

        if (distSq > ARRIVAL_DISTANCE_SQ && anchorDistSq > NEAR_STATION_DISTANCE_SQ) {
            if (gameTime >= nextStandReacquireTick) {
                nextStandReacquireTick = gameTime + STAND_REACQUIRE_INTERVAL_TICKS;
                BlockPos refreshed = refreshStandPosition(level, villager, stationAnchor);
                if (refreshed != null) standPos = refreshed;
            }
            return false;
        }
        nextStandReacquireTick = 0L;
        return true;
    }

    /**
     * Re-query a fresh stand position when the villager drifts off-stand.
     * Default returns null (no refresh). Subclasses that use building snapshots should override
     * to look up a new stand near stationAnchor in the current worksite snapshot.
     */
    protected @Nullable BlockPos refreshStandPosition(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos stationAnchor) {
        return null;
    }

    protected final void transition(ProducerState newState, long gameTime) {
        state = newState;
        stateEnteredTick = gameTime;
    }

    protected final void transitionToNavigationState(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        transition(isVillagerAtWorksite(level, villager) ? ProducerState.PATH_TO_STATION : ProducerState.PATH_TO_WORKSITE, gameTime);
    }

    protected final void abandonCurrentStation(ServerLevel level, VillagerEntityMCA villager, long gameTime, boolean markUsed) {
        if (markUsed && stationAnchor != null) {
            abandonedUntilByStation.put(stationAnchor.asLong(), gameTime + ABANDONED_STATION_COOLDOWN_TICKS);
        }
        releaseStationClaim(level, villager, stationAnchor);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        stationAnchor = null;
        standPos = null;
        activeRecipe = null;
        stagedInputs.clear();
        transitionToNavigationState(level, villager, gameTime);
    }

    protected final void resetWorksiteTargeting() {
        currentWorksiteTarget = null;
        currentWorksiteTargetKind = "stand";
        worksiteTargetProgress.reset();
        worksiteTargetFailures.reset();
    }

    protected final void setBlocked(
            ServerLevel level, VillagerEntityMCA villager, long gameTime,
            ProducerBlockedReason reason, @Nullable String detail) {
        blocked = reason;
        announceBlocked(level, villager, gameTime, reason, detail);
    }

    private void clearTransientState() {
        stationAnchor = null;
        standPos = null;
        activeRecipe = null;
        pendingOutput = ItemStack.EMPTY;
        stagedInputs.clear();
        recipeCooldownUntil.clear();
        abandonedUntilByStation.clear();
        recipeAttempts = 0;
        idleUntilTick = 0L;
        resetWorksiteTargeting();
    }

    protected final void clearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        onClearAll(level, villager, gameTime);
        releaseStationClaim(level, villager, stationAnchor);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        clearTransientState();
        state = ProducerState.PATH_TO_WORKSITE;
        stateEnteredTick = gameTime;
    }

    // ── WorkTaskAdapter ──

    @Override
    public WorkSiteRef activeWorkSite(ServerLevel level, VillagerEntityMCA villager) {
        return resolveWorksite(level, villager);
    }

    @Override
    public WorkTarget activeWorkTarget(ServerLevel level, VillagerEntityMCA villager) {
        if (currentWorksiteTarget != null && state == ProducerState.PATH_TO_WORKSITE) {
            return WorkTarget.buildingApproach(currentWorksiteTarget, worksiteReference(villager), currentWorksiteTargetKind);
        }
        if (standPos == null || stationAnchor == null) return null;
        return WorkTarget.stationStand(standPos, stationAnchor, state.name().toLowerCase());
    }

    @Override
    public float navigationWalkSpeed(ServerLevel level, VillagerEntityMCA villager) {
        return WALK_SPEED;
    }

    @Override
    public int navigationCloseEnough(ServerLevel level, VillagerEntityMCA villager) {
        return CLOSE_ENOUGH;
    }

    @Override
    public double navigationArrivalDistanceSq(ServerLevel level, VillagerEntityMCA villager) {
        return ARRIVAL_DISTANCE_SQ;
    }

    @Override
    public String navigationState(ServerLevel level, VillagerEntityMCA villager) {
        return state.name();
    }

    @Override
    public String navigationBlockedState(ServerLevel level, VillagerEntityMCA villager) {
        return blocked.name();
    }

    // ── Shared helpers ──

    protected static Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    protected static boolean isFatigueGated(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        //? if neoforge {
        CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        return FatigueData.isGated(fatigue) || FatigueData.getFatigue(fatigue) >= FatigueData.DROWSY_THRESHOLD;
    }
}
