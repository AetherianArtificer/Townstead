package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.jetbrains.annotations.Nullable;

/**
 * Side-effecting bridge between Townstead's {@link LifeCycle} and MCA's
 * {@link AgeState}. Reads a villager's persisted {@link TownsteadVillager.Life}
 * state, resolves the current canonical stage from the calendar, and returns
 * the MCA {@link AgeState} to commit (with {@code SENIOR} collapsed onto
 * {@code ADULT} for compatibility with MCA's hardcoded marriage/work gates).
 *
 * <p>Updates {@code Life.currentStageId} and {@code Life.isSenior} as a side
 * effect. The {@code setAgeState} mixin calls this from server context only.</p>
 */
public final class LifeStageProgression {

    private LifeStageProgression() {}

    // Set while Townstead drives a villager's MCA breeding age from its resolved stage (see
    // syncMcaAgeToStage). The ageless/immortal setAge-freeze mixin checks this so a stage-driven
    // write lands while vanilla per-tick aging stays blocked.
    private static final ThreadLocal<Boolean> DRIVING_AGE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** True while {@link #syncMcaAgeToStage} is writing the breeding age (lets the freeze mixin pass it). */
    public static boolean isDrivingAge() {
        return DRIVING_AGE.get();
    }

    /**
     * Maintain the apparent-age freeze stamp and return the day to treat as "now" for
     * display. When villager aging is disabled, stamp the current day on first sight and
     * hold it, so displayed apparent age / stage / senior progress stop advancing with the
     * calendar; when aging is enabled, clear any stale stamp so display resumes live.
     * Steady state touches nothing (no NBT churn).
     */
    public static long agingDisplayDay(TownsteadVillager.Life life, long today) {
        if (life == null) return today;
        if (freezesAging(life)) {
            if (!life.hasAgingFrozenDay()) life.setAgingFrozenDay(today);
            return life.agingFrozenDay();
        }
        if (life.hasAgingFrozenDay()) life.clearAgingFrozenDay();
        return today;
    }

    /** Read-only variant: honor an existing freeze stamp without creating or clearing one. */
    public static long agingDisplayDayView(TownsteadVillager.Life life, long today) {
        if (life != null && freezesAging(life) && life.hasAgingFrozenDay()) {
            return life.agingFrozenDay();
        }
        return today;
    }

    /**
     * Whether this villager's biological clock is held fixed in calendar time, so its apparent age,
     * stage, and render size stop advancing. True when the global "villagers do not age" config is on,
     * the villager is ageless (e.g. skeletons), or it is immortal (the potion/gene flag). Both ageless
     * and immortal are a per-villager frozen clock: the admin still picks an apparent age with the
     * continuous slider, it just never advances on its own. (Immortal additionally can't die.)
     *
     * <p>Keyed on {@link TownsteadVillager.Life} so the display-day helpers can call it without an
     * entity; a config-driven MCA <em>trait</em> conferring immortality (rare, see
     * {@code TraitEffects.isImmortal}) is only seen by the entity-aware {@link #isStageFrozen}, so such
     * a villager still holds its stage but its displayed age may climb. The shipped immortal gene sets
     * {@code life.immortal()}, so it is fully frozen here.</p>
     */
    private static boolean freezesAging(TownsteadVillager.Life life) {
        return TownsteadConfig.isVillagerAgingDisabled() || isAgeless(life) || life.immortal();
    }

    /**
     * Resolve the MCA {@link AgeState} this villager should be in right now.
     * Returns {@code null} if Townstead has no usable state and MCA's original
     * resolution should win (no birth stamped yet, life cycle data missing,
     * length mismatch).
     *
     * <p>If the villager is {@link TownsteadVillager.Life#immortal} and already
     * has a {@code currentStageId}, the recorded stage is held fixed regardless
     * of calendar days. If immortal but the stage isn't yet known, falls through
     * to normal resolution.</p>
     *
     * <p>If the villager has stage data but no birth stamp yet, this stamps one
     * back-dated to align with MCA's currently-requested stage, so subsequent
     * progression is smooth.</p>
     */
    @Nullable
    public static AgeState resolveMcaAgeState(VillagerEntityMCA villager, AgeState requested) {
        if (villager == null) return null;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return null;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        TownsteadVillager.Life life = state.life();
        if (!life.hasStageDays()) return null;

        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty()) return null;
        if (life.stageDaysLength() != cycle.size()) return null;

        long today = TownsteadCalendar.lifeDay(server);

        // First contact: no birth stamped → back-date so we agree with MCA's initial state.
        if (!life.hasBirth()) {
            stampBirthForRequested(life, cycle, requested, today);
        }

        // Stage-frozen (ageless or immortal) or aging globally disabled, + stage recorded: hold the
        // recorded stage fixed. With no stage recorded yet we fall through once to resolve the current
        // stage and commit it, then freeze on that from here on.
        if ((isStageFrozen(villager, life) || TownsteadConfig.isVillagerAgingDisabled())
                && !life.currentStageId().isEmpty()) {
            return resolveFromRecordedStage(life, cycle, requested);
        }

        LifeStageResolver.Resolved resolved = LifeStageResolver.resolve(
                cycle, life.stageDays(), life.birthWorldDay(), today);
        if (resolved == null) return null;

        boolean wasSenior = life.isSenior();
        commit(life, resolved);
        if (wasSenior != life.isSenior()) {
            if (life.isSenior()) SeniorEffects.applySenior(villager);
            else SeniorEffects.clearSenior(villager);
        }
        return canonicalToMca(resolved.stage().presentsAs(), requested);
    }

    /**
     * True if the stage resolved from the stored birth still matches the villager's
     * live MCA {@link AgeState}. Used to detect a stale birth (e.g. an older build
     * stamped an adult's birth at ~today, so it now reads as a baby) that the
     * too-old self-heal can't catch. Returns {@code true} — "no action" — whenever
     * we can't judge (no birth/cycle, stage frozen, length mismatch).
     */
    public static boolean birthMatchesBody(VillagerEntityMCA villager) {
        if (villager == null) return true;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return true;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (isStageFrozen(villager, life) || !life.hasBirth() || !life.hasStageDays()) return true;

        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty() || life.stageDaysLength() != cycle.size()) return true;

        // Honor the apparent-age freeze: when aging is disabled the body is intentionally
        // held at the frozen stage, so resolve against the frozen day too. Using the live
        // day here makes the body look "stale" once the calendar advances, which the
        // stamper's self-heal then "fixes" by re-fabricating a birth, wiping editor edits.
        long today = agingDisplayDayView(life, TownsteadCalendar.lifeDay(server));
        LifeStageResolver.Resolved resolved = LifeStageResolver.resolve(
                cycle, life.stageDays(), life.birthWorldDay(), today);
        if (resolved == null) return true;

        AgeState body = villager.getAgeState();
        return canonicalToMca(resolved.stage().presentsAs(), body) == body;
    }

    /**
     * Ageless: the villager's biological clock never advances by the calendar (e.g. skeletons), so its
     * stage and apparent age hold fixed. The admin still picks a frozen apparent age with the continuous
     * slider; it just never advances. Sources: the granted Potion of Agelessness flag, or an intrinsic
     * {@code ageless} life cycle. See also {@link #freezesAging}.
     */
    public static boolean isAgeless(TownsteadVillager.Life life) {
        if (life.ageless()) return true; // granted by the Potion of Agelessness
        LifeCycle cycle = resolveCycle(life);
        return cycle != null && cycle.ageless(); // intrinsic to the species' life cycle
    }

    /**
     * Operational immortality: the potion/gene flag ({@link TownsteadVillager.Life#immortal}) or a
     * config-driven MCA trait conferring it. Immortal villagers are frozen in time like the ageless
     * (eternal youth) and additionally cannot die.
     */
    private static boolean isImmortal(VillagerEntityMCA villager, TownsteadVillager.Life life) {
        return life.immortal()
                || com.aetherianartificer.townstead.origin.trait.TraitEffects.isImmortal(villager);
    }

    /** Whether this villager's life stage is held fixed, by immortality or by an ageless life cycle. */
    private static boolean isStageFrozen(VillagerEntityMCA villager, TownsteadVillager.Life life) {
        return isImmortal(villager, life) || isAgeless(life);
    }

    /**
     * True when the villager is below adult by its Townstead life stage (baby/toddler/child/teen). A
     * defensive gate for adult-only interactions (marriage, employment): MCA gates those on vanilla
     * {@code isBaby()} (breeding age &lt; 0), which {@link #syncMcaAgeToStage} keeps in step with the
     * stage, but this reads the resolved stage directly so it stays correct even in the brief window
     * before a freshly loaded villager's breeding age is re-synced. Falls back to {@code isBaby()} when
     * the villager has no resolved Townstead stage. Senior presents as adult, so it is NOT pre-adult.
     */
    public static boolean isPreAdult(VillagerEntityMCA villager) {
        if (villager == null) return false;
        LifeStage stage = currentStage(villager);
        if (stage == null) return villager.isBaby();
        CanonicalStage c = stage.presentsAs();
        return c != CanonicalStage.ADULT && c != CanonicalStage.SENIOR;
    }

    @Nullable
    private static LifeCycle resolveCycle(TownsteadVillager.Life life) {
        ResourceLocation originId = ResourceLocation.tryParse(life.originId());
        if (originId == null) originId = OriginRegistry.DEFAULT_ID;
        return OriginRegistry.effectiveLifeCycle(originId);
    }

    private static void stampBirthForRequested(TownsteadVillager.Life life, LifeCycle cycle,
                                               AgeState requested, long today) {
        CanonicalStage canonical = mcaToCanonical(requested);
        // Pick the first stage matching the requested canonical, fallback first stage.
        int index = 0;
        for (int i = 0; i < cycle.size(); i++) {
            if (cycle.stageAt(i).presentsAs() == canonical) {
                index = i;
                break;
            }
        }
        long backstep = LifeStageResolver.cumulativeDaysBefore(life.stageDays(), index);
        life.setBirth(today - backstep, false);
    }

    private static AgeState resolveFromRecordedStage(TownsteadVillager.Life life, LifeCycle cycle, AgeState requested) {
        LifeStage stage = cycle.findById(life.currentStageId()).orElse(null);
        if (stage == null) return null;
        return canonicalToMca(stage.presentsAs(), requested);
    }

    /**
     * The villager's current life stage (from its recorded {@code currentStageId}), or null when it has no
     * cycle or hasn't resolved a stage yet. Server-side; used to gate per-stage behavior (movement, needs,
     * dialogue) without a client round-trip. {@code currentStageId} is committed by {@code setAgeState} and
     * the daily ticker, so it is set shortly after spawn.
     */
    @org.jetbrains.annotations.Nullable
    public static LifeStage currentStage(VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty()) return null;
        String id = life.currentStageId();
        if (id == null || id.isEmpty()) return null;
        return cycle.findById(id).orElse(null);
    }

    /**
     * True when this villager is at a stage flagged {@code talkable:false} (e.g. an egg). Drives MCA's
     * {@code isToYoungToSpeak} so the stage's dialogue comes out as babble (like a baby) instead of real
     * lines, while the inspect GUI, the editor, and trading all still work. Runs on both sides (the
     * client TTS path and the server message transform both consult MCA's speech gate), so unlike a
     * server-only interaction block it does not guard on {@code isClientSide}.
     */
    public static boolean isMuteStage(VillagerEntityMCA villager) {
        if (villager == null) return false;
        LifeStage stage = currentStage(villager);
        return stage != null && !stage.talkable();
    }

    /**
     * Apply an explicit editor age choice. This intentionally bypasses the
     * global "villagers do not age" freeze for this one edit: that setting
     * should stop time-based progression, not reject a player/admin choosing a
     * new age. If aging is disabled, re-anchor the freeze day to the edit day so
     * the selected apparent age stays stable afterward.
     */
    public static void applyManualAgeEdit(VillagerEntityMCA villager, long newBirthWorldDay) {
        if (villager == null) return;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return;

        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        life.setBirth(newBirthWorldDay, true);

        long today = TownsteadCalendar.lifeDay(server);
        // Re-anchor the freeze stamp to the edit day so a frozen-in-time villager (ageless, immortal,
        // or globally non-aging) holds the chosen apparent age instead of resuming from a stale day.
        if (freezesAging(life)) {
            life.setAgingFrozenDay(today);
        }

        // Commit the new stage from the freshly stamped birth, then re-drive MCA's
        // AgeState through our resolver so the body actually moves to that stage.
        // setAge alone only changes breeding age, leaving getAgeState() stale; the
        // stamper's coherence self-heal then sees body != birth-derived stage and
        // re-fabricates a mid-stage birth, overwriting this very edit (set 5, get 8).
        // commit first so the (aging-disabled) resolver reads the updated recorded stage.
        commitStageFromBirth(villager, life, today);
        villager.setAgeState(villager.getAgeState());
        // Drive MCA's breeding age to the new stage so the body resizes immediately, not a tick later.
        syncMcaAgeToStage(villager);
    }

    @Nullable
    private static CanonicalStage commitStageFromBirth(VillagerEntityMCA villager, TownsteadVillager.Life life, long today) {
        if (life == null || !life.hasBirth() || !life.hasStageDays()) return null;

        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty() || life.stageDaysLength() != cycle.size()) return null;

        LifeStageResolver.Resolved resolved = LifeStageResolver.resolve(
                cycle, life.stageDays(), life.birthWorldDay(), today);
        if (resolved == null) return null;

        boolean wasSenior = life.isSenior();
        commit(life, resolved);
        if (wasSenior != life.isSenior()) {
            if (life.isSenior()) SeniorEffects.applySenior(villager);
            else SeniorEffects.clearSenior(villager);
        }
        return resolved.stage().presentsAs();
    }

    /**
     * Fabricate a birth life-day that places a villager mid-way through the stage
     * matching {@code state}, using their rolled {@code stageDays}. Replaces the
     * old human-decade heuristic, which would land spawned adults past death now
     * that a whole cycle spans only a few game-years. Returns "today" if the
     * cycle/stageDays aren't ready (caller still marks a birth so display works).
     */
    public static long fabricateBirthLifeDay(VillagerEntityMCA villager, MinecraftServer server, AgeState state) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        long today = TownsteadCalendar.lifeDay(server);
        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty() || life.stageDaysLength() != cycle.size()) return today;

        CanonicalStage canonical = mcaToCanonical(state);
        int index = 0;
        for (int i = 0; i < cycle.size(); i++) {
            if (cycle.stageAt(i).presentsAs() == canonical) {
                index = i;
                break;
            }
        }
        int[] days = life.stageDays();
        long before = LifeStageResolver.cumulativeDaysBefore(days, index);
        long within = Math.max(1, days[index]) / 2L; // mid-stage → a stable, sensible starting age
        return today - (before + within);
    }

    private static void commit(TownsteadVillager.Life life, LifeStageResolver.Resolved resolved) {
        life.setCurrentStageId(resolved.stage().id());
        life.setSenior(resolved.stage().presentsAs() == CanonicalStage.SENIOR);
        life.setFertility(fertilityFor(resolved.stage().presentsAs()));
    }

    /**
     * Re-resolve the current stage from the stored birth + stage days and commit it,
     * driven by the server tick rather than MCA's {@code setAgeState}. MCA only calls
     * setAgeState on its own AgeState transitions (baby→…→adult) and never again once
     * adult, so without this an adult villager would never progress into the Townstead
     * SENIOR stage (no hair desaturation, no speed penalty) and a stale/re-scaled birth
     * would never be reconciled. Returns true if the senior flag changed (caller can
     * re-broadcast the life sync so client hair desaturation updates).
     *
     * <p>No-op for stage-frozen (ageless or immortal) villagers with a frozen stage, or when
     * birth/cycle/stageDays are absent or mismatched.</p>
     */
    public static boolean tickResolveStage(VillagerEntityMCA villager) {
        if (villager == null) return false;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return false;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        // Maintain the apparent-age freeze stamp even for unsynced villagers, so it lands
        // within a day of the toggle rather than whenever a player next observes them.
        agingDisplayDay(life, TownsteadCalendar.lifeDay(server));
        if (TownsteadConfig.isVillagerAgingDisabled()) return false;
        if (isStageFrozen(villager, life) && !life.currentStageId().isEmpty()) return false;
        if (!life.hasBirth() || !life.hasStageDays()) return false;

        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty() || life.stageDaysLength() != cycle.size()) return false;

        long today = TownsteadCalendar.lifeDay(server);
        LifeStageResolver.Resolved resolved = LifeStageResolver.resolve(
                cycle, life.stageDays(), life.birthWorldDay(), today);
        if (resolved == null) return false;

        boolean wasSenior = life.isSenior();
        commit(life, resolved);
        boolean seniorChanged = wasSenior != life.isSenior();
        if (seniorChanged) {
            if (life.isSenior()) SeniorEffects.applySenior(villager);
            else SeniorEffects.clearSenior(villager);
        }
        return seniorChanged;
    }

    /**
     * Stage-level fertility snapshot. setAgeState fires on stage transitions, so
     * we can only commit a value per stage entry; within-stage ramps would need a
     * tick-driven update and aren't load-bearing for v1.
     */
    private static float fertilityFor(CanonicalStage canonical) {
        if (canonical == null) return 0f;
        return switch (canonical) {
            // Teens are infertile by design: MCA's marriage/breeding gate treats every
            // pre-adult stage as isBaby() (age < 0), and Townstead keeps TEEN negative,
            // so only adults/seniors can ever breed. Keep this 0 so the value can't
            // authorize teen pregnancy if something starts reading fertility later.
            case BABY, TODDLER, CHILD, TEEN -> 0f;
            case ADULT -> 1.0f;
            case SENIOR -> 0.3f;
        };
    }

    private static AgeState canonicalToMca(CanonicalStage canonical, AgeState fallback) {
        if (canonical == null) return fallback;
        return switch (canonical) {
            case BABY -> AgeState.BABY;
            case TODDLER -> AgeState.TODDLER;
            case CHILD -> AgeState.CHILD;
            case TEEN -> AgeState.TEEN;
            // SENIOR collapses onto ADULT so MCA's marriage/work gates still work.
            case ADULT, SENIOR -> AgeState.ADULT;
        };
    }

    /**
     * The MCA breeding-age that renders the proportions of a stage presenting as
     * {@code canonical}. Lets the editor show a model matching the labeled stage
     * instead of a linear age sweep. SENIOR renders as ADULT (MCA has no senior).
     */
    public static int representativeMcaAge(CanonicalStage canonical) {
        int max = AgeState.getMaxAge();
        int k = canonicalToMca(canonical, AgeState.ADULT).ordinal(); // BABY=1 .. ADULT=5
        if (k >= AgeState.ADULT.ordinal()) return 0;
        return -max + (k - 1) * (max / 4);
    }

    /**
     * Keep MCA's breeding age, which is the only input to its interpolated body dimensions, consistent
     * with the villager's resolved life stage, so the body renders at the stage's graded size instead of
     * whatever age it last held. MCA recomputes dimensions only inside {@code setAge}, so a calendar- or
     * editor-driven stage change otherwise leaves the body stuck (a child rendering at adult size).
     *
     * <p>Only pre-adult stages are forced: an adult (or senior, which presents as adult) renders at full
     * size from any non-negative age, and forcing it would reset vanilla love-mode cooldowns. The write
     * goes through the ageless/immortal {@code setAge} freeze via {@link #DRIVING_AGE}, so a frozen
     * villager reaches its stage size once and then holds it (vanilla per-tick aging stays blocked).</p>
     */
    public static void syncMcaAgeToStage(VillagerEntityMCA villager) {
        if (villager == null || villager.getAgeState() == AgeState.ADULT) return;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (!life.hasBirth() || !life.hasStageDays()) return;
        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty() || life.stageDaysLength() != cycle.size()) return;

        long today = agingDisplayDayView(life, TownsteadCalendar.lifeDay(server));
        long bioAge = Math.max(0L, today - life.birthWorldDay());
        int modelAge = modelAgeForBio(cycle, life.stageDays(), bioAge);
        if (modelAge >= 0 || villager.getAge() == modelAge) return;

        boolean prev = DRIVING_AGE.get();
        DRIVING_AGE.set(Boolean.TRUE);
        try {
            villager.setAge(modelAge);
        } finally {
            DRIVING_AGE.set(prev);
        }
    }

    /**
     * The MCA breeding age that renders stage-correct proportions for a biological age, interpolated
     * across the cycle's stages (piecewise-linear between each stage's {@link #representativeMcaAge}).
     * Mirrors the editor's preview-age math so the in-world body matches what the slider shows.
     */
    private static int modelAgeForBio(LifeCycle cycle, int[] stageDays, long bioAge) {
        int n = cycle.size();
        if (n == 0) return 0;
        long cum = 0;
        for (int i = 0; i < n; i++) {
            int d = Math.max(1, (stageDays != null && i < stageDays.length) ? stageDays[i] : 1);
            if (bioAge < cum + d || i == n - 1) {
                float frac = (float) Math.max(0.0, Math.min(1.0, (bioAge - cum) / (double) d));
                int a = representativeMcaAge(cycle.stageAt(i).presentsAs());
                int b = representativeMcaAge(cycle.stageAt(Math.min(n - 1, i + 1)).presentsAs());
                return offBoundary(Math.round(a + (b - a) * frac), a, b);
            }
            cum += d;
        }
        return representativeMcaAge(cycle.stageAt(n - 1).presentsAs());
    }

    /**
     * Keep a model age a hair inside its AgeState band. MCA's per-age dimension interpolation spikes
     * toward the next stage's size exactly at a band boundary (the representative ages land on those
     * boundaries), so a value sitting on one makes the body briefly balloon at a stage transition. A
     * small margin off both edges removes the spike without visibly changing proportions.
     */
    private static int offBoundary(int value, int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        if (hi - lo < 4) return value;
        int g = Math.max(1, (hi - lo) / 48);
        if (value < lo + g) return lo + g;
        if (value > hi - g) return hi - g;
        return value;
    }

    /**
     * Progress 0..1000 (permil) through the villager's senior stage, or {@code 0}
     * if not senior / no cycle / data incoherent. Stable across reloads since
     * everything it reads is persisted in {@link TownsteadVillager.Life}.
     */
    public static int seniorProgressPermil(VillagerEntityMCA villager) {
        if (villager == null) return 0;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return 0;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (!life.isSenior() || !life.hasStageDays() || !life.hasBirth()) return 0;

        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty()) return 0;
        if (life.stageDaysLength() != cycle.size()) return 0;

        int seniorIndex = -1;
        for (int i = 0; i < cycle.size(); i++) {
            if (cycle.stageAt(i).presentsAs() == CanonicalStage.SENIOR) {
                seniorIndex = i;
                break;
            }
        }
        if (seniorIndex < 0) return 0;

        int[] days = life.stageDays();
        long cumulative = LifeStageResolver.cumulativeDaysBefore(days, seniorIndex);
        long today = agingDisplayDayView(life, TownsteadCalendar.lifeDay(server));
        long daysAlive = Math.max(0L, today - life.birthWorldDay());
        long daysIntoSenior = daysAlive - cumulative;
        int seniorSpan = Math.max(1, days[seniorIndex]);

        long permil = (daysIntoSenior * 1000L) / seniorSpan;
        if (permil < 0L) permil = 0L;
        if (permil > 1000L) permil = 1000L;
        return (int) permil;
    }

    /**
     * Push a fresh life-sync packet for every loaded senior villager, so
     * client-side hair desaturation tracks the day-by-day progress through the
     * senior stage. Called from the MC-day-rollover hook in
     * {@link com.aetherianartificer.townstead.calendar.WorldCalendarTicker}.
     */
    public static void broadcastDailyUpdates(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof VillagerEntityMCA villager)) continue;
                TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
                if (!life.isSenior()) continue;
                VillagerLifeSyncPayload payload = Townstead.townstead$lifeSync(villager);
                if (payload == null) continue;
                //? if neoforge {
                PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
                //?} else if forge {
                /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, payload);
                *///?}
            }
        }
    }

    private static CanonicalStage mcaToCanonical(AgeState state) {
        if (state == null) return CanonicalStage.ADULT;
        return switch (state) {
            case BABY -> CanonicalStage.BABY;
            case TODDLER -> CanonicalStage.TODDLER;
            case CHILD -> CanonicalStage.CHILD;
            case TEEN -> CanonicalStage.TEEN;
            case ADULT, UNASSIGNED -> CanonicalStage.ADULT;
        };
    }
}
