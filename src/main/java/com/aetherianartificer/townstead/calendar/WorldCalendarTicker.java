package com.aetherianartificer.townstead.calendar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Drives {@link WorldCalendarSavedData#worldDayCounter} forward from the
 * overworld's {@code getDayTime()}. Standardizing on {@code dayTime} (rather
 * than {@code getGameTime()} or {@code tickCount}) makes the calendar
 * compatible with Time Control / slow-time mods that stretch in-game day
 * length without altering wall-clock ticks. This mirrors the needs system's
 * clock choice — see memory {@code project_time_scaling}.
 *
 * The ticker is cheap: O(1) per server tick, no allocations on the common
 * path, only one {@code setDirty()} per crossed day boundary.
 */
public final class WorldCalendarTicker {
    private static final long TICKS_PER_DAY = 24000L;

    /**
     * If dayTime jumps by more than this in a single tick we treat it as a
     * teleport (e.g., {@code /time set}) and rebase rather than advancing the
     * counter by the entire span. One in-game week of slack.
     */
    private static final long REBASE_THRESHOLD_TICKS = TICKS_PER_DAY * 7L;

    private WorldCalendarTicker() {}

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        long current = overworld.getDayTime();

        if (!data.hasLastSample()) {
            data.primeSample(current);
            return;
        }

        long delta = current - data.lastDayTimeSample();
        if (delta == 0L) return;

        // /time set going backwards, or an admin teleporting day count forward
        // by weeks — both indicate a discontinuity rather than elapsed game
        // time. Rebase silently.
        if (delta < 0L || delta > REBASE_THRESHOLD_TICKS) {
            data.advance(current, 0L, 0);
            return;
        }

        long residue = data.subDayResidueTicks() + delta;
        int daysAdvanced = (int) (residue / TICKS_PER_DAY);
        // advance() folds the residue back into [0, 24000) using floorMod on
        // the supplied delta, so we pass the raw delta along with the days
        // we computed against the previous residue.
        data.advance(current, delta, daysAdvanced);
    }
}
