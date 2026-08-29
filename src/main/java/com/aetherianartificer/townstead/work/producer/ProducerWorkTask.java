package com.aetherianartificer.townstead.work.producer;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.work.WorkMovement;
import com.aetherianartificer.townstead.work.WorkNavigationResult;
import com.aetherianartificer.townstead.work.WorkSiteView;
import com.aetherianartificer.townstead.work.order.Order;
import com.aetherianartificer.townstead.work.order.OrderContext;
import com.aetherianartificer.townstead.work.order.OrderList;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.storage.PhysicalStorageDelivery;
import com.aetherianartificer.townstead.storage.StorageUse;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;

import java.util.List;
import com.aetherianartificer.townstead.work.WorkTarget;
import com.aetherianartificer.townstead.work.WorkTaskAdapter;
import com.aetherianartificer.townstead.work.WorkTargetFailures;
import com.aetherianartificer.townstead.work.WorkTargetProgress;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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
    /** How long a stood-down worker rests before glancing at the list again. */
    protected static final int STAND_DOWN_IDLE_TICKS =
            com.aetherianartificer.townstead.work.WorkRest.REST_TICKS;
    protected static final int PRODUCE_STATE_TIMEOUT_TICKS = 160;
    protected static final int COLLECT_STATE_TIMEOUT_TICKS = 80;

    public enum ProducerState {
        PATH_TO_WORKSITE, PATH_TO_STATION, RECONCILE_STATION,
        SELECT_RECIPE, ACQUIRE_SUPPLIES, GATHER, PRODUCE, COLLECT, COLLECT_WAIT,
        DELIVER
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

    public enum PreparationStatus { READY, WAITING, BLOCKED }

    /** A station prerequisite which may need real navigation before ingredients are gathered. */
    public record PreparationResult(PreparationStatus status, @Nullable String detail) {
        public static PreparationResult ready() { return new PreparationResult(PreparationStatus.READY, null); }
        public static PreparationResult waiting() { return new PreparationResult(PreparationStatus.WAITING, null); }
        public static PreparationResult blocked(String detail) {
            return new PreparationResult(PreparationStatus.BLOCKED, detail);
        }
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
    /** A scheduler/activity pause must not erase a batch already handed to a physical station. */
    private boolean resumeCommittedCycle;
    /** A shelf visit currently being walked, kept stable while the worker paths to it. */
    private @Nullable WorkIngredients.PhysicalPull physicalPull;
    /** Reusable tools fetched for this cycle; pre-existing tools are deliberately not recorded. */
    private final Set<ResourceLocation> borrowedToolIds = new HashSet<>();
    /** Storage destinations which refused this delivery during the current pass. */
    private final Set<Long> rejectedDeliveryStorage = new HashSet<>();
    private @Nullable BlockPos deliveryTarget;
    private boolean deliveryFinalized;

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

    protected abstract @Nullable WorkSiteView resolveWorksite(ServerLevel level, VillagerEntityMCA villager);

    protected abstract boolean isVillagerAtWorksite(ServerLevel level, VillagerEntityMCA villager);

    protected abstract @Nullable BlockPos resolveWorksiteTarget(
            ServerLevel level, VillagerEntityMCA villager, long gameTime, WorkSiteView site);

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
            ServerLevel level, VillagerEntityMCA villager, long gameTime,
            Predicate<ResourceLocation> outputAllowed);

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

    /** The next storage block this recipe must physically visit, or null when hands are stocked. */
    protected @Nullable WorkIngredients.PhysicalPull nextPhysicalPull(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return null;
    }

    /** Worksite cells used when choosing storage for finished goods and returned tools. */
    protected Set<Long> transferWorksiteBounds(ServerLevel level, VillagerEntityMCA villager) {
        return Set.of();
    }

    /** Whether this carried stack is finished work belonging to the active cycle. */
    protected boolean isCycleOutput(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty() || activeRecipe == null) return false;
        return activeRecipe.output().equals(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    // ── XP ──

    /** The code-driven engine's stable completed-work activity. */
    protected String activityKey() {
        return "townstead:produced";
    }

    /**
     * Credits the worker's own career for a finished job.
     *
     * <p>The career is read off the villager rather than named by the engine: a career levels
     * itself, so a Baker working a kitchen earns Baker, not Cook. Trades with something extra to
     * say — a taste appraisal, a mod-loaded gate — override; the rest need only name their
     * counter.</p>
     */
    protected void awardProductionXp(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) return;
        ResourceLocation profession = net.minecraft.core.registries.BuiltInRegistries
                .VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        if (profession == null) return;
        int xp = Math.max(1, activeRecipe.tier());
        com.aetherianartificer.townstead.profession.career.CareerProgression.completeWork(
                villager,
                com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(profession),
                xp, gameTime, activityKey(), activeRecipe.output(), "item", activeRecipe.tier());
    }

    // ── Optional hooks (default no-op) ──

    protected void onProduceTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    /** Prepares a physical station before gathering; entity-powered stations fetch a driver here. */
    protected PreparationResult prepareStation(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return PreparationResult.ready();
    }

    /** A committed input set vanished without producing an output and must be reloaded. */
    protected boolean isProductionInterrupted(ServerLevel level, VillagerEntityMCA villager,
                                              long gameTime) { return false; }

    /** Clears engine-specific interruption bookkeeping after the base restarts the cycle. */
    protected void onProductionInterrupted(ServerLevel level, VillagerEntityMCA villager,
                                           long gameTime) {}

    protected boolean mustWaitBeyondCollectTimeout(ServerLevel level, VillagerEntityMCA villager) { return false; }

    protected void onSessionRefresh(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void onSessionRelease(ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {}

    /** A counted order reached its real target after this station's output was stored. */
    protected void onOrderCompleted(ServerLevel level, VillagerEntityMCA villager,
                                    @Nullable BlockPos pos, long gameTime) {}

    /** No actionable station/recipe remains at this worksite. */
    protected void onNoWork(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    /**
     * A physical station cycle was abandoned before delivery.  This is distinct from releasing
     * the station's concurrency claim: an entity-operated machine may also be holding a worker or
     * animal in the world, and its concrete task must disengage that participant here.
     */
    protected void onStationAbandoned(ServerLevel level, VillagerEntityMCA villager,
                                      @Nullable BlockPos pos, long gameTime) {}

    /**
     * Whether a committed station cycle can survive a scheduler-level Behavior rollover.
     * Concrete station engines answer from their cycle record; ordinary producers have nothing
     * to resume.  This is deliberately a state question, not a longer timeout.
     */
    protected boolean hasResumableStationSession(
            ServerLevel level, VillagerEntityMCA villager, @Nullable BlockPos pos, long gameTime) {
        return false;
    }

    protected void onOpportunisticSweep(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void onStop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void onClearAll(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    protected void playGatherSound(ServerLevel level, VillagerEntityMCA villager) {}

    protected void announceBlocked(
            ServerLevel level, VillagerEntityMCA villager, long gameTime,
            ProducerBlockedReason reason, @Nullable String detail) {}

    protected void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {}

    /** What to call this worker in diagnostics. */
    protected String debugLabel() {
        return "Worker";
    }

    /**
     * Narrates to the nearest player when villager-AI debugging is on.
     *
     * <p>Lives in the base so every producing trade explains itself, not just the one that
     * happened to grow the plumbing first. A trade nobody can interrogate is a trade whose bugs
     * get diagnosed by guesswork.</p>
     */
    protected void debugChat(ServerLevel level, VillagerEntityMCA villager, String message) {
        if (!com.aetherianartificer.townstead.TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (!(level.getNearestPlayer(villager, DEBUG_CHAT_RANGE)
                instanceof net.minecraft.server.level.ServerPlayer player)) return;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[" + debugLabel() + ":" + villager.getName().getString() + "] " + message));
    }

    private static final int DEBUG_CHAT_RANGE = 24;

    /** The last refusal reported, so a standing gate is stated once rather than every tick. */
    private @Nullable String lastGateReported;

    /**
     * Reports why the task will not start — once per change, because these are asked several
     * times a second and a repeated line is noise rather than information.
     *
     * <p>The start gates were the one silent stretch in the whole loop: a villager failing any of
     * them simply stood there, which is indistinguishable from every other failure at a glance.</p>
     */
    private boolean gate(ServerLevel level, VillagerEntityMCA villager, boolean ok, String reason) {
        if (ok) return true;
        if (!reason.equals(lastGateReported)) {
            lastGateReported = reason;
            debugChat(level, villager, "GATE:" + reason);
        }
        return false;
    }

    protected int stateTimeoutTicks(ProducerState state) {
        return switch (state) {
            case ACQUIRE_SUPPLIES -> MAX_DURATION;
            case GATHER -> GATHER_STATE_TIMEOUT_TICKS;
            case PRODUCE -> PRODUCE_STATE_TIMEOUT_TICKS;
            case COLLECT, COLLECT_WAIT -> COLLECT_STATE_TIMEOUT_TICKS;
            case DELIVER -> MAX_DURATION;
            default -> DEFAULT_STATE_TIMEOUT_TICKS;
        };
    }

    // ── Behavior lifecycle ──

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!gate(level, villager, isTaskEnabled(), "task disabled (mod or config)")) return false;
        if (!gate(level, villager, !isFatigueGated(villager), "too tired to work")) return false;
        if (!gate(level, villager, isEligibleVillager(level, villager),
                "not eligible — no workplace seat, or does not do this work")) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (!gate(level, villager, !brain.isPanicking() && villager.getLastHurtByMob() == null,
                "panicking or recently hurt")) return false;
        boolean committed = hasResumableStationSession(level, villager, stationAnchor, level.getGameTime())
                || hasPendingPhysicalTransfer(level, villager);
        if (!gate(level, villager, currentActivity(villager) == Activity.WORK || committed,
                "off shift — schedule is not WORK")) return false;
        lastGateReported = null;
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isEligibleVillager(level, villager) && !hasPendingPhysicalTransfer(level, villager)) return;
        if (resumeCommittedCycle && hasPendingPhysicalTransfer(level, villager)) {
            resumeCommittedCycle = false;
            transition(ProducerState.DELIVER, gameTime);
            return;
        }
        if (resumeCommittedCycle
                && hasResumableStationSession(level, villager, stationAnchor, gameTime)) {
            resumeCommittedCycle = false;
            blocked = ProducerBlockedReason.NONE;
            claimStation(level, villager, gameTime);
            transition(ProducerState.RECONCILE_STATION, gameTime);
            return;
        }
        resumeCommittedCycle = false;
        blocked = ProducerBlockedReason.NONE;
        state = ProducerState.PATH_TO_WORKSITE;
        stateEnteredTick = gameTime;
        recipeAttempts = 0;
        abandonedUntilByStation.clear();
        physicalPull = null;
        borrowedToolIds.clear();
        rejectedDeliveryStorage.clear();
        deliveryTarget = null;
        deliveryFinalized = false;
        resetWorksiteTargeting();
        com.aetherianartificer.townstead.reaction.trigger.event.TaskEventBridge.onStart(level, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isTaskEnabled()) return false;
        if (!isEligibleVillager(level, villager) && !hasPendingPhysicalTransfer(level, villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        return currentActivity(villager) == Activity.WORK
                || hasResumableStationSession(level, villager, stationAnchor, gameTime)
                || hasPendingPhysicalTransfer(level, villager);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Behavior's duration is a scheduler lease, not a production lifecycle. If that lease
        // rolls over while this is still the villager's enabled, eligible WORK activity, retain
        // the committed station cycle. The physical claim is still released below, so the next
        // behavior must reacquire the station normally. A shift boundary preserves only an
        // already-committed physical cycle; completion, abandonment, job loss and disablement
        // release it explicitly.
        VillagerBrain<?> brain = villager.getVillagerBrain();
        boolean pendingTransfer = hasPendingPhysicalTransfer(level, villager);
        boolean resumeCommitted = pendingTransfer || (isTaskEnabled()
                && (isEligibleVillager(level, villager) || hasPendingPhysicalTransfer(level, villager))
                && !brain.isPanicking()
                && villager.getLastHurtByMob() == null
                && (hasResumableStationSession(level, villager, stationAnchor, gameTime)
                || pendingTransfer));
        releaseStationClaim(level, villager, stationAnchor);
        if (resumeCommitted) {
            // The same Behavior instance will be restarted by WORK. Keep its recipe, station,
            // order claim and subclass protocol state intact; reconciliation reads the machine's
            // real state before doing anything else.
            resumeCommittedCycle = true;
            return;
        }
        resumeCommittedCycle = false;
        onStop(level, villager, gameTime);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        releaseOrderClaim();
        String reactionStopReason = blocked != ProducerBlockedReason.NONE ? "give_up" : null;
        clearTransientState();
        state = ProducerState.PATH_TO_WORKSITE;
        com.aetherianartificer.townstead.reaction.trigger.event.TaskEventBridge.onStop(level, villager, reactionStopReason);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!isEligibleVillager(level, villager) && !hasPendingPhysicalTransfer(level, villager)) {
            clearAll(level, villager, gameTime);
            return;
        }
        if (gameTime < idleUntilTick) return;

        if (isVillagerAtWorksite(level, villager)
                && com.aetherianartificer.townstead.profession.PoilessTradingProfessions
                        .contains(villager.getVillagerData().getProfession())
                && villager.shouldRestock()) {
            villager.restock();
        }

        debugTick(level, villager, gameTime);

        int timeout = stateTimeoutTicks(state);
        if (gameTime - stateEnteredTick > timeout
                && state != ProducerState.PATH_TO_WORKSITE
                && state != ProducerState.PATH_TO_STATION
                && state != ProducerState.RECONCILE_STATION
                && state != ProducerState.ACQUIRE_SUPPLIES
                && state != ProducerState.PRODUCE
                && state != ProducerState.COLLECT_WAIT
                && state != ProducerState.DELIVER) {
            debugChat(level, villager, "STATE:timeout in " + state.name() + ", resetting");
            transitionToNavigationState(level, villager, gameTime);
            onStationAbandoned(level, villager, stationAnchor, gameTime);
            releaseStationClaim(level, villager, stationAnchor);
            onSessionRelease(level, villager, stationAnchor, gameTime);
            releaseOrderClaim();
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
            case ACQUIRE_SUPPLIES -> tickAcquireSupplies(level, villager, gameTime);
            case GATHER -> tickGather(level, villager, gameTime);
            case PRODUCE -> tickProduce(level, villager, gameTime);
            case COLLECT -> tickCollect(level, villager, gameTime);
            case COLLECT_WAIT -> tickCollectWait(level, villager, gameTime);
            case DELIVER -> tickDeliver(level, villager, gameTime);
        }
    }

    // ── State: PATH_TO_WORKSITE ──

    private void tickPathToWorksite(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor != null) {
            onStationAbandoned(level, villager, stationAnchor, gameTime);
        }
        releaseStationClaim(level, villager, stationAnchor);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        stationAnchor = null;
        standPos = null;
        activeRecipe = null;
        stagedInputs.clear();

        WorkSiteView site = resolveWorksite(level, villager);
        lastWorksite = site == null ? null : site.site();
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
            onNoWork(level, villager, gameTime);
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
            // Station acquisition may carry a speculative autonomous recipe so it can rank
            // viable station/recipe pairs. An empty station has committed to none of it yet and
            // must pass through the one authoritative selector: that is where ordered output and
            // list-only/stand-down policy are applied. Skipping straight to GATHER let a worker
            // obey an order once, reacquire the same empty station, then brew the acquisition
            // guess instead of consulting the list.
            case EMPTY_READY -> transition(ProducerState.SELECT_RECIPE, gameTime);
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

    // ── Orders ──
    //
    // A worksite's order list gets first refusal over what this worker would otherwise choose.
    // Deliberately a filter and not a second scorer: one place decides, and it decides in the order
    // the player wrote. A worksite with no orders costs nothing here — the list is checked for
    // emptiness before anything else is asked.

    /** The place this villager last resolved to, remembered so orders need no second resolution. */
    @Nullable private Worksite lastWorksite;

    /** The line the active job was taken from, held so it can be credited or released. */
    @Nullable private com.aetherianartificer.townstead.work.order.Order claimedLine;
    /** Output units reserved on that line; batching stations can claim more than one. */
    private int claimedLineAmount;
    /** Number of copies of the active recipe physically committed in this cycle. */
    private int activeBatchOperations = 1;

    /** The worksite this villager is working, for subclasses that need to ask it things. */
    @Nullable
    protected Worksite activeWorksite() {
        return lastWorksite;
    }

    /** The order line the active job was taken from, for engines whose lines carry more than
     * a count — a commission's escrowed workpiece is collected at gather. */
    @Nullable
    protected com.aetherianartificer.townstead.work.order.Order claimedOrder() {
        return claimedLine;
    }

    /** Physical recipe copies planned for the current cycle. */
    protected int activeBatchOperations() {
        return Math.max(1, activeBatchOperations);
    }

    /** Maximum copies this station can execute together. Subclasses opt into batching. */
    protected int maximumBatchOperations(ServerLevel level, VillagerEntityMCA villager,
                                         ProducerRecipe recipe) {
        return 1;
    }

    /** Hands a claim back when a job ends without producing. Safe to call when nothing is held. */
    protected void releaseOrderClaim() {
        if (claimedLine != null) {
            claimedLine.abandon(claimedLineAmount);
            claimedLine = null;
            claimedLineAmount = 0;
            activeBatchOperations = 1;
            markOrdersChanged();
        }
    }

    /** Credits the ordered line for what was just stored. */
    private boolean creditOrderClaim(ServerLevel level, VillagerEntityMCA villager, int count) {
        boolean completed = false;
        if (claimedLine != null) {
            Order credited = claimedLine;
            claimedLine.finish(claimedLineAmount, count);
            OrderContext context = orderContext(level, villager);
            completed = context != null && credited.satisfied(context);
            claimedLine = null;
            claimedLineAmount = 0;
            activeBatchOperations = 1;
            markOrdersChanged();
        }
        return completed;
    }

    /**
     * Tells any open screen that a line moved. Claiming, finishing and abandoning are the three
     * things that change a row's status and its produced count, and all three pass through here —
     * which is why the screen no longer has to keep asking whether they have.
     */
    private void markOrdersChanged() {
        if (lastWorksite != null) lastWorksite.bumpOrdersRevision();
    }

    /**
     * Everything this worker could make right now, for orders to choose among. Defaults to nothing,
     * which leaves orders inert for engines that have not opted in yet — an engine that cannot
     * enumerate its options should keep choosing for itself rather than half-obey a list.
     */
    protected List<? extends ProducerRecipe> orderCandidates(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return List.of();
    }

    /**
     * How an order's questions about the world get answered.
     *
     * <p>Concrete, and the same for every producing trade — this used to be a null default that
     * only the cook overrode, which quietly made orders a cooking feature. None of it is about
     * cooking: stock is counted over the worksite's own extent, the census is the village's, and
     * eligibility is a named villager and a rank. A butcher's smokehouse answers all three exactly
     * as a kitchen does.</p>
     *
     * <p>Null only when there is no worksite to ask about, which is the one case where guessing
     * would make "keep twenty in stock" produce forever.</p>
     */
    @Nullable
    protected OrderContext orderContext(ServerLevel level, VillagerEntityMCA villager) {
        Worksite site = activeWorksite();
        return site == null ? null
                : com.aetherianartificer.townstead.work.order.WorksiteOrders
                        .contextFor(level, site, villager);
    }

    private @Nullable ProducerRecipe chooseRecipe(
            ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        OrderList orders = lastWorksite == null ? null : lastWorksite.orders();
        if (orders != null && !orders.isEmpty()) {
            OrderContext context = orderContext(level, villager);
            if (context != null) {
                List<? extends ProducerRecipe> candidates = orderCandidates(level, villager, gameTime);
                OrderList.Pick ordered = orders.firstWorkable(candidates, context);
                if (ordered != null) {
                    // Claimed now, so a second worker reading the same list sees this one spoken
                    // for rather than both starting the last of a batch. The line comes back with
                    // the recipe: several lines may name the same item, and crediting the first of
                    // them would retire the wrong one.
                    releaseOrderClaim();
                    claimedLine = ordered.order();
                    int outputPerOperation = Math.max(1, ordered.recipe().outputCount());
                    int maximumOperations = Math.max(1,
                            maximumBatchOperations(level, villager, ordered.recipe()));
                    int outstanding = Math.max(1, claimedLine.outstanding(context));
                    activeBatchOperations = Math.max(1, Math.min(maximumOperations,
                            (outstanding + outputPerOperation - 1) / outputPerOperation));
                    claimedLineAmount = Math.min(outstanding,
                            activeBatchOperations * outputPerOperation);
                    claimedLine.claim(claimedLineAmount);
                    markOrdersChanged();
                    debugChat(level, villager, "SELECT:ordered " + ordered.recipe().output()
                            + " x" + claimedLineAmount);
                    return ordered.recipe();
                }
            }
        }
        // Standing down is checked last and needs no context: reading the list is a question about
        // the world, but "stop" is a flat instruction about the place. Gating it behind a context
        // made it a cook-only setting, since no other engine had one — a Beverage Artisan told to stand down
        // carried on brewing. It binds every producer at this worksite, list or no list, engine or
        // no engine.
        if (orders != null && orders.listOnly()) return null;
        Predicate<ResourceLocation> autonomousOutputAllowed = orders == null || orders.isEmpty()
                ? output -> true
                : output -> !orders.governs(output);
        return pickRecipe(level, villager, gameTime, autonomousOutputAllowed);
    }

    private void tickSelectRecipe(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!ensureNearStation(level, villager, gameTime)) return;
        if (stationAnchor == null) {
            transition(ProducerState.PATH_TO_STATION, gameTime);
            return;
        }

        ProducerRecipe recipe = chooseRecipe(level, villager, gameTime);
        if (recipe == null) {
            onNoWork(level, villager, gameTime);
            OrderList orders = lastWorksite == null ? null : lastWorksite.orders();
            if (orders != null && orders.listOnly()) {
                // Stood down with nothing workable on the list: rest on their feet rather than
                // grind the selector. The claim is released so the station is free, the idle
                // lets the brain wander them around the shop, and the status says why.
                debugChat(level, villager, "SELECT:standing down");
                setBlocked(level, villager, gameTime, ProducerBlockedReason.STANDING_DOWN, null);
                idleUntilTick = gameTime + STAND_DOWN_IDLE_TICKS;
                abandonCurrentStation(level, villager, gameTime, false);
                return;
            }
            debugChat(level, villager, "SELECT:no recipe, rotating");
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_RECIPE, null);
            abandonCurrentStation(level, villager, gameTime, true);
            return;
        }

        activeRecipe = recipe;
        recipeAttempts = 0;
        physicalPull = null;
        rejectedDeliveryStorage.clear();
        deliveryTarget = null;
        deliveryFinalized = false;
        transition(ProducerState.ACQUIRE_SUPPLIES, gameTime);
    }

    // ── State: ACQUIRE_SUPPLIES ──

    private void tickAcquireSupplies(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            physicalPull = null;
            transition(ProducerState.SELECT_RECIPE, gameTime);
            return;
        }
        if (physicalPull == null) {
            physicalPull = nextPhysicalPull(level, villager, gameTime);
            if (physicalPull == null) {
                transition(ProducerState.GATHER, gameTime);
                return;
            }
        }
        BlockPos source = physicalPull.source();
        if (source == null) {
            physicalPull = null;
            return;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(villager, source, WALK_SPEED, 1);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(source));
        if (villager.distanceToSqr(
                source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5) > 5.0d) {
            return;
        }

        WorkIngredients.PhysicalPull request = physicalPull;
        WorkIngredients.PhysicalPullResult pulled = WorkIngredients.executePhysicalPull(
                level, villager, request);
        physicalPull = null;
        if (pulled.count() <= 0) {
            // A stale slot is ordinary contention, not a recipe failure. Re-plan against the
            // freshly invalidated index on the next tick.
            debugChat(level, villager, "ACQUIRE:source changed for " + request.detail());
            stateEnteredTick = gameTime;
            return;
        }
        if (request.reusable()) borrowedToolIds.addAll(pulled.itemIds());
        villager.swing(villager.getDominantHand());
        debugChat(level, villager, "ACQUIRE:" + pulled.count() + " " + request.detail()
                + " from " + source.toShortString());
        stateEnteredTick = gameTime;
    }

    // ── State: GATHER ──

    private void tickGather(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (activeRecipe == null) {
            transition(ProducerState.SELECT_RECIPE, gameTime);
            return;
        }

        PreparationResult preparation = prepareStation(level, villager, gameTime);
        if (preparation.status() == PreparationStatus.WAITING) {
            // Entity travel is governed by observed movement and station state, not the short
            // timeout used for pulling a few items from storage.
            stateEnteredTick = gameTime;
            return;
        }
        if (preparation.status() == PreparationStatus.BLOCKED) {
            debugChat(level, villager, "PREPARE:blocked"
                    + (preparation.detail() == null ? "" : " (" + preparation.detail() + ")"));
            releaseOrderClaim();
            rollbackGather(level, villager, gameTime);
            setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_DRIVER,
                    preparation.detail());
            idleUntilTick = gameTime + IDLE_BACKOFF;
            abandonCurrentStation(level, villager, gameTime, false);
            return;
        }
        if (!ensureNearStation(level, villager, gameTime)) return;

        GatherResult result = gatherInputs(level, villager, gameTime);
        if (!result.success()) {
            debugChat(level, villager, "GATHER:failed for " + activeRecipe.output()
                    + (result.detail() == null ? "" : " (" + result.detail() + ")"));
            releaseOrderClaim();
            rollbackGather(level, villager, gameTime);
            onStationAbandoned(level, villager, stationAnchor, gameTime);
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
            // The station refused this hand-off; that condemns this physical station, not the
            // recipe at every other compatible station. The per-station abandonment below keeps
            // us from retrying the covered/busy block while allowing an immediate skillet try.
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

        if (isProductionInterrupted(level, villager, gameTime)) {
            debugChat(level, villager, "PRODUCE:inputs left station without output; reloading recipe");
            onProductionInterrupted(level, villager, gameTime);
            releaseOrderClaim();
            onStationAbandoned(level, villager, stationAnchor, gameTime);
            onSessionRelease(level, villager, stationAnchor, gameTime);
            releaseStationClaim(level, villager, stationAnchor);
            stationAnchor = null;
            standPos = null;
            activeRecipe = null;
            stagedInputs.clear();
            transitionToNavigationState(level, villager, gameTime);
            return;
        }

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
        onSessionRelease(level, villager, stationAnchor, gameTime);
        releaseStationClaim(level, villager, stationAnchor);
        rejectedDeliveryStorage.clear();
        deliveryTarget = null;
        deliveryFinalized = false;
        if (hasPendingPhysicalTransfer(level, villager)) {
            transition(ProducerState.DELIVER, gameTime);
            return;
        }
        finishDelivery(level, villager, gameTime);
    }

    // ── State: DELIVER ──

    private void tickDeliver(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        boolean deliveringOutput = hasCarriedCycleOutput(level, villager);
        Predicate<ItemStack> matcher = deliveringOutput
                ? stack -> isCycleOutput(level, stack)
                : this::isBorrowedTool;
        StorageUse storageUse = deliveringOutput ? StorageUse.OUTPUT : StorageUse.TOOL_RETURN;
        if (!hasMatching(villager, matcher)) {
            finishDelivery(level, villager, gameTime);
            return;
        }

        Set<Long> bounds = transferWorksiteBounds(level, villager);
        if (deliveryTarget == null) {
            deliveryTarget = PhysicalStorageDelivery.findDestination(
                    level, villager, bounds, matcher, rejectedDeliveryStorage, storageUse);
            if (deliveryTarget == null) {
                setBlocked(level, villager, gameTime, ProducerBlockedReason.NO_STORAGE,
                        hasCarriedCycleOutput(level, villager) ? "finished goods" : "borrowed tools");
                idleUntilTick = gameTime + 40L;
                return;
            }
        }

        BehaviorUtils.setWalkAndLookTargetMemories(villager, deliveryTarget, WALK_SPEED, 1);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                new BlockPosTracker(deliveryTarget));
        if (villager.distanceToSqr(deliveryTarget.getX() + 0.5,
                deliveryTarget.getY() + 0.5, deliveryTarget.getZ() + 0.5) > 5.0d) return;

        int moved = PhysicalStorageDelivery.depositMatchingAt(
                level, villager, deliveryTarget, matcher, storageUse);
        villager.swing(villager.getDominantHand());
        if (moved <= 0) rejectedDeliveryStorage.add(deliveryTarget.asLong());
        deliveryTarget = null;
        stateEnteredTick = gameTime;
        if (!hasPendingPhysicalTransfer(level, villager)) finishDelivery(level, villager, gameTime);
    }

    /** Completes accounting only after the real stacks have reached storage. */
    private void finishDelivery(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (deliveryFinalized) return;
        deliveryFinalized = true;
        boolean completedOrder = creditOrderClaim(level, villager, activeRecipe == null ? 1
                : Math.max(1, activeRecipe.outputCount()) * activeBatchOperations());
        pendingOutput = ItemStack.EMPTY;
        awardProductionXp(level, villager, gameTime);

        debugChat(level, villager, "COLLECT:done " + (activeRecipe != null ? activeRecipe.output() : "null"));
        activeRecipe = null;
        stagedInputs.clear();
        physicalPull = null;
        borrowedToolIds.clear();
        rejectedDeliveryStorage.clear();
        deliveryTarget = null;
        if (completedOrder) {
            onOrderCompleted(level, villager, stationAnchor, gameTime);
        }

        // Reconsider the whole ordered station set after every delivery. Restricting selection to
        // the station already under the worker made a lower coffee line win repeatedly at a pot
        // after it consumed the roasted beans protected by the higher skillet line. The station
        // and recipe indexes are cached, so this is one cheap global decision on the next tick;
        // when the same station is still correct there is no extra walk.
        stationAnchor = null;
        standPos = null;
        // Re-resolve the worksite after every delivery. Usually this is the same room and costs
        // one cheap arrival check; for multi-worksite professions it is the point where a worker
        // may return to their primary site or accept another building's pending order.
        transition(ProducerState.PATH_TO_WORKSITE, gameTime);

        // A schedule-independent coordinator may have kept WORK active solely to finish this
        // batch. Hand control back immediately; do not select an after-hours recipe before
        // vanilla's next periodic schedule refresh.
        Activity scheduled = currentActivity(villager);
        if (scheduled != Activity.WORK) {
            villager.getBrain().setActiveActivityIfPossible(scheduled);
        }
    }

    private boolean hasPendingPhysicalTransfer(ServerLevel level, VillagerEntityMCA villager) {
        return hasCarriedCycleOutput(level, villager) || hasMatching(villager, this::isBorrowedTool);
    }

    private boolean hasCarriedCycleOutput(@Nullable ServerLevel level, VillagerEntityMCA villager) {
        return hasMatching(villager, stack -> isCycleOutput(level, stack));
    }

    private boolean isBorrowedTool(ItemStack stack) {
        if (stack == null || stack.isEmpty() || borrowedToolIds.isEmpty()) return false;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem());
        return id != null && borrowedToolIds.contains(id);
    }

    private static boolean hasMatching(VillagerEntityMCA villager, Predicate<ItemStack> matcher) {
        if (villager == null || matcher == null) return false;
        net.minecraft.world.SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (matcher.test(inventory.getItem(slot))) return true;
        }
        return false;
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
        // Being close to the station block is not enough: a wall can be between the two.
        // The selected stand is the navigable side of the station, so work starts only there.
        if (!isAtStationStand(distSq)) {
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

    static boolean isAtStationStand(double distanceSq) {
        return distanceSq <= ARRIVAL_DISTANCE_SQ;
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
        onStationAbandoned(level, villager, stationAnchor, gameTime);
        releaseStationClaim(level, villager, stationAnchor);
        onSessionRelease(level, villager, stationAnchor, gameTime);
        releaseOrderClaim();
        stationAnchor = null;
        standPos = null;
        activeRecipe = null;
        physicalPull = null;
        stagedInputs.clear();
        if (hasMatching(villager, this::isBorrowedTool)) {
            rejectedDeliveryStorage.clear();
            deliveryTarget = null;
            deliveryFinalized = false;
            transition(ProducerState.DELIVER, gameTime);
            return;
        }
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
        resumeCommittedCycle = false;
        stationAnchor = null;
        standPos = null;
        activeRecipe = null;
        pendingOutput = ItemStack.EMPTY;
        physicalPull = null;
        borrowedToolIds.clear();
        rejectedDeliveryStorage.clear();
        deliveryTarget = null;
        deliveryFinalized = false;
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
    public WorkSiteView activeWorkSite(ServerLevel level, VillagerEntityMCA villager) {
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
        return com.aetherianartificer.townstead.shift.VillagerSchedules.currentActivity(villager);
    }

    protected static boolean isFatigueGated(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        return needs.gated() || needs.fatigue() >= FatigueData.DROWSY_THRESHOLD;
    }
}
