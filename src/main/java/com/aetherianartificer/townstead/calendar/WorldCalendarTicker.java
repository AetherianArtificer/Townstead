package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Random;

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

    /**
     * Run once per save when {@code calendarInitialized} is false. Decides
     * the starting state of the calendar:
     *
     * <ul>
     *   <li>Wizard / admin pre-set values (calendarInitialized=true on disk)
     *       — never reach this method.</li>
     *   <li>Fresh world ({@code dayTime &lt; 24000}) with randomization
     *       enabled — roll a year in the configured range and a day-of-year
     *       in {@code [0, profile.daysPerYear)}, push {@code dayTime}
     *       forward (time-of-day preserved) so seasonal mods pick the season
     *       too, set the epoch offset, seed the counter.</li>
     *   <li>Existing save ({@code dayTime &gt;= 24000}) OR randomization
     *       disabled — seed {@code worldDayCounter} from
     *       {@code dayTime / 24000} so saves predating Townstead get
     *       credited the vanilla days they've already elapsed.</li>
     * </ul>
     *
     * Returns the (possibly mutated) dayTime value to use as the prime
     * baseline.
     */
    private static long initializeCalendar(MinecraftServer server, ServerLevel overworld,
                                           WorldCalendarSavedData data, long current) {
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        int dpy = (profile != null && profile.daysPerYear() > 0) ? profile.daysPerYear() : 360;

        boolean wantsRandom = TownsteadConfig.isCalendarRandomizeStartEnabled()
                && current < TICKS_PER_DAY;

        if (wantsRandom) {
            int min = TownsteadConfig.getCalendarStartYearMin();
            int max = TownsteadConfig.getCalendarStartYearMax();
            if (max < min) max = min;

            Random rng = new Random();
            int year = (max == min) ? min : min + rng.nextInt(max - min + 1);
            int dayOfYear = rng.nextInt(dpy);

            long timeOfDay = Math.floorMod(current, TICKS_PER_DAY);
            long newDayTime = (long) dayOfYear * TICKS_PER_DAY + timeOfDay;
            overworld.setDayTime(newDayTime);
            data.setWorldDayCounter(dayOfYear);
            data.setEpochYearOffset(year);

            Townstead.LOGGER.info("[Calendar] Fresh world: rolled start = year {}, day-of-year {} (dpy={})",
                    year, dayOfYear + 1, dpy);
            return newDayTime;
        }

        // Seed from elapsed dayTime so existing saves get retroactive credit
        // for the days they've already lived. Brand-new worlds with
        // randomization disabled seed to 0, which is correct.
        long elapsedDays = Math.max(0L, current / TICKS_PER_DAY);
        if (elapsedDays > 0L && data.worldDayCounter() == 0L) {
            data.setWorldDayCounter(elapsedDays);
            Townstead.LOGGER.info("[Calendar] Seeded worldDayCounter to {} from existing dayTime", elapsedDays);
        }
        return current;
    }

    public static void tick(MinecraftServer server) {
        if (server == null) return;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return;

        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        long current = overworld.getDayTime();

        if (!data.calendarInitialized()) {
            current = initializeCalendar(server, overworld, data, current);
            data.markCalendarInitialized();
        }

        if (!data.realClockCatchupApplied()) {
            data.markRealClockCatchupApplied();
            if (TownsteadCalendar.isRealClockMode(server)) {
                int catchupDays = data.applyRealClockCatchup();
                if (catchupDays > 0) {
                    Townstead.LOGGER.info("[Calendar] Real-clock catch-up: advanced worldDayCounter by {} real day{}",
                            catchupDays, catchupDays == 1 ? "" : "s");
                    AgeableCatchup.applyBulkCatchup(server, catchupDays);
                    ShiftScheduleApplier.reapplyWeeklySchedules(server);
                    Townstead.townstead$broadcastCalendarSync(server);
                }
            }
        }

        if (!data.hasLastSample()) {
            data.primeSample(current);
            return;
        }

        long lastSample = data.lastDayTimeSample();
        long delta = current - lastSample;
        if (delta == 0L) return;

        // /time set going backwards, or an admin teleporting day count forward
        // by weeks — both indicate a discontinuity rather than elapsed game
        // time. Rebase silently.
        if (delta < 0L || delta > REBASE_THRESHOLD_TICKS) {
            data.advance(current, 0L, 0);
            return;
        }

        int daysAdvanced = (int) (Math.floorDiv(current, TICKS_PER_DAY)
                - Math.floorDiv(lastSample, TICKS_PER_DAY));
        data.advance(current, delta, daysAdvanced);
        if (daysAdvanced > 0) {
            // Keep AgeableMob.lastSeenWorldDay stamps current so autosaves
            // between sessions persist the latest value (otherwise the next
            // load would see a stale stamp and incorrectly catch up).
            AgeableCatchup.refreshLastSeenStamps(server);
            // Day-of-week changed: swap in each weekly-mode villager's schedule
            // for the new day. Once per in-game day, off the per-tick hot path.
            ShiftScheduleApplier.reapplyWeeklySchedules(server);
            Townstead.townstead$broadcastCalendarSync(server);
        }
    }
}
