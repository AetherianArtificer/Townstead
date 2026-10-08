package com.aetherianartificer.townstead.root;

import org.jetbrains.annotations.Nullable;

/**
 * Pure resolver: given a villager's {@link LifeCycle}, persisted {@code stageDays},
 * and (birth, current) world-day pair, return the stage they're currently in.
 *
 * <p>No villager handle, no Minecraft state — testable in isolation. The mutable
 * companion that reads/writes {@code Life} from a real villager lives in
 * {@link LifeStageProgression}.</p>
 */
public final class LifeStageResolver {

    private LifeStageResolver() {}

    public record Resolved(int stageIndex, LifeStage stage, float deltaInStage) {}

    /** Whether villagers may reach senior stages. A world setting, read through here to keep this class pure. */
    private static java.util.function.BooleanSupplier seniorsAllowed = () -> true;

    public static void setSeniorsAllowed(java.util.function.BooleanSupplier allowed) {
        seniorsAllowed = allowed == null ? () -> true : allowed;
    }

    /**
     * Resolve the stage for {@code daysAlive = currentWorldDay - birthWorldDay}.
     * Returns null if data is incoherent (empty cycle, length mismatch).
     */
    @Nullable
    public static Resolved resolve(LifeCycle cycle, int[] stageDays, long birthWorldDay, long currentWorldDay) {
        Resolved resolved = resolveRaw(cycle, stageDays, birthWorldDay, currentWorldDay);
        return resolved == null || seniorsAllowed.getAsBoolean() ? resolved : withoutSenior(cycle, resolved);
    }

    /** With seniors off, a villager past adulthood stays at the last stage before the senior ones. */
    static Resolved withoutSenior(LifeCycle cycle, Resolved resolved) {
        if (resolved.stage().presentsAs() != CanonicalStage.SENIOR) return resolved;
        for (int i = resolved.stageIndex() - 1; i >= 0; i--) {
            if (cycle.stageAt(i).presentsAs() != CanonicalStage.SENIOR) return new Resolved(i, cycle.stageAt(i), 1f);
        }
        return resolved;
    }

    @Nullable
    private static Resolved resolveRaw(LifeCycle cycle, int[] stageDays, long birthWorldDay, long currentWorldDay) {
        if (cycle == null || cycle.isEmpty()) return null;
        if (stageDays == null || stageDays.length != cycle.size()) return null;
        long daysAlive = Math.max(0L, currentWorldDay - birthWorldDay);

        long cumulative = 0L;
        for (int i = 0; i < stageDays.length; i++) {
            long next = cumulative + Math.max(0, stageDays[i]);
            if (daysAlive < next) {
                int span = Math.max(1, stageDays[i]);
                float delta = (float) (daysAlive - cumulative) / (float) span;
                if (delta < 0f) delta = 0f;
                if (delta > 1f) delta = 1f;
                return new Resolved(i, cycle.stageAt(i), delta);
            }
            cumulative = next;
        }

        // Past the last stage: apply its end action.
        int last = cycle.size() - 1;
        StageEndAction onEnd = cycle.effectiveEndAction(last);
        if (onEnd == StageEndAction.LOOP && cumulative > 0L) {
            long wrappedDaysAlive = daysAlive % cumulative;
            return resolveFromDaysAlive(cycle, stageDays, wrappedDaysAlive);
        }
        return new Resolved(last, cycle.stageAt(last), 1f);
    }

    @Nullable
    private static Resolved resolveFromDaysAlive(LifeCycle cycle, int[] stageDays, long daysAlive) {
        long cumulative = 0L;
        for (int i = 0; i < stageDays.length; i++) {
            long next = cumulative + Math.max(0, stageDays[i]);
            if (daysAlive < next) {
                int span = Math.max(1, stageDays[i]);
                float delta = (float) (daysAlive - cumulative) / (float) span;
                return new Resolved(i, cycle.stageAt(i), delta);
            }
            cumulative = next;
        }
        int last = cycle.size() - 1;
        return new Resolved(last, cycle.stageAt(last), 1f);
    }

    /** Cumulative day count up to but not including the stage at {@code stageIndex}. */
    public static long cumulativeDaysBefore(int[] stageDays, int stageIndex) {
        long sum = 0L;
        for (int i = 0; i < stageIndex && i < stageDays.length; i++) {
            sum += Math.max(0, stageDays[i]);
        }
        return sum;
    }
}
