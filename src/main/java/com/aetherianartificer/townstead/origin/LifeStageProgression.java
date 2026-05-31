package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
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

        // Immortal + stage recorded: hold the recorded stage fixed.
        if (isImmortal(villager, life) && !life.currentStageId().isEmpty()) {
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
     * we can't judge (no birth/cycle, immortal frozen, length mismatch).
     */
    public static boolean birthMatchesBody(VillagerEntityMCA villager) {
        if (villager == null) return true;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return true;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (isImmortal(villager, life) || !life.hasBirth() || !life.hasStageDays()) return true;

        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty() || life.stageDaysLength() != cycle.size()) return true;

        long today = TownsteadCalendar.lifeDay(server);
        LifeStageResolver.Resolved resolved = LifeStageResolver.resolve(
                cycle, life.stageDays(), life.birthWorldDay(), today);
        if (resolved == null) return true;

        AgeState body = villager.getAgeState();
        return canonicalToMca(resolved.stage().presentsAs(), body) == body;
    }

    /**
     * Operational immortality: the non-heritable potion flag ({@link TownsteadVillager.Life#immortal})
     * OR a heritable trait conferring {@code life.immortal}. Both pin the life stage.
     */
    private static boolean isImmortal(VillagerEntityMCA villager, TownsteadVillager.Life life) {
        return life.immortal()
                || com.aetherianartificer.townstead.origin.trait.TraitEffects.isImmortal(villager);
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
     * Freeze an immortal villager's appearance at the given cycle index,
     * committing that stage's senior/fertility state and MCA age model without
     * touching the date of birth (calendar age keeps climbing). No-op for an out
     * of range index or a villager with no cycle. Intended for immortals — for a
     * mortal the next calendar resolution would override {@code currentStageId}.
     */
    public static void freezeAtStage(VillagerEntityMCA villager, int stageIndex) {
        if (villager == null) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        LifeCycle cycle = resolveCycle(life);
        if (cycle == null || cycle.isEmpty()) return;
        if (stageIndex < 0 || stageIndex >= cycle.size()) return;

        LifeStage stage = cycle.stageAt(stageIndex);
        boolean wasSenior = life.isSenior();
        life.setCurrentStageId(stage.id());
        life.setSenior(stage.presentsAs() == CanonicalStage.SENIOR);
        life.setFertility(fertilityFor(stage.presentsAs()));
        if (wasSenior != life.isSenior()) {
            if (life.isSenior()) SeniorEffects.applySenior(villager);
            else SeniorEffects.clearSenior(villager);
        }
        villager.setAgeState(villager.getAgeState());
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
     * <p>No-op for immortals with a frozen stage, or when birth/cycle/stageDays are
     * absent or mismatched.</p>
     */
    public static boolean tickResolveStage(VillagerEntityMCA villager) {
        if (villager == null) return false;
        MinecraftServer server = villager.level().getServer();
        if (server == null) return false;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (isImmortal(villager, life) && !life.currentStageId().isEmpty()) return false;
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
        long today = TownsteadCalendar.lifeDay(server);
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
