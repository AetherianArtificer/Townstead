package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

/**
 * Server-side facade for everything calendar-related. Composes:
 *   - {@link WorldCalendarSavedData} (counter + offset + override)
 *   - {@link CalendarProfileRegistry} (data-pack-loaded profiles)
 *   - {@link CalendarTypes} (Java-side compute strategies)
 *   - {@link CalendarCompat} (auto-resolution from detected mods)
 *
 * Returns {@link CalendarDate#UNKNOWN} only as a defensive fallback if no
 * profile has loaded yet (pre-reload-listener call, e.g., very early server
 * startup). The reload listener fires before world data loads, so the normal
 * path always has profiles available.
 */
public final class TownsteadCalendar {
    private TownsteadCalendar() {}

    public static CalendarDate today(MinecraftServer server) {
        return dateOf(server, worldDay(server));
    }

    public static CalendarDate dateOf(MinecraftServer server, long worldDay) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = activeProfile(server);
        if (profile == null) return CalendarDate.UNKNOWN;
        CalendarType type = CalendarTypes.resolveDriverFor(profile.id());
        if (type == null) return CalendarDate.UNKNOWN;
        return type.compute(server, profile, worldDay, data.epochYearOffset());
    }

    public static long worldDay(MinecraftServer server) {
        return WorldCalendarSavedData.get(server).worldDayCounter();
    }

    public static int epochYearOffset(MinecraftServer server) {
        return WorldCalendarSavedData.get(server).epochYearOffset();
    }

    public static void setEpochYearOffset(MinecraftServer server, int offset) {
        WorldCalendarSavedData.get(server).setEpochYearOffset(offset);
        rebroadcastAfterCalendarChange(server);
    }

    /**
     * Rebase {@code epochYearOffset} so today's display year equals
     * {@code displayYear}. Counter is untouched, so all stored worldDay-based
     * dates (future Phase 2 DOBs / village establishments) shift only in
     * their displayed-year representation.
     */
    public static void rebaseToDisplayYear(MinecraftServer server, int displayYear) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = activeProfile(server);
        if (profile == null) return;
        CalendarType type = CalendarTypes.resolveDriverFor(profile.id());
        if (type == null) return;
        CalendarDate now = type.compute(server, profile, data.worldDayCounter(), 0);
        data.setEpochYearOffset(displayYear - now.year());
        rebroadcastAfterCalendarChange(server);
    }

    /**
     * Force the world-day counter and push a fresh calendar sync to clients.
     * This is the only correct entry point for the {@code set-day} command:
     * poking {@link WorldCalendarSavedData#setWorldDayCounter} directly updates
     * the server but leaves clients showing their cached date.
     */
    public static void setWorldDay(MinecraftServer server, long worldDay) {
        WorldCalendarSavedData.get(server).setWorldDayCounter(worldDay);
        rebroadcastAfterCalendarChange(server);
    }

    /**
     * Move the calendar so "today" displays as the given date, <em>without</em>
     * aging anyone. Returns the resulting {@link CalendarDate}, or {@code null}
     * if {@code month}/{@code day} are out of range for that year's layout
     * (e.g. day 30 of a 28-day month) or no profile/driver is available.
     *
     * <p>This is purely a display change, by design:
     * <ul>
     *   <li>The <b>year</b> is set with the epoch offset alone (exactly like
     *       {@link #rebaseToDisplayYear}); the day counter is untouched by this
     *       step. Villager ages and village foundings are computed from counter
     *       <em>differences</em>, and the offset cancels out of that
     *       subtraction, so nobody ages.</li>
     *   <li>The <b>month/day</b> is set by nudging the counter only <em>within
     *       the current year</em>. The move stays inside the same year bucket,
     *       so the displayed year (and therefore every age) is unchanged.</li>
     * </ul>
     * The weekday falls wherever the counter's natural {@code mod daysPerWeek}
     * cycle lands. Aligning it to a specific real-world weekday is impossible
     * without shifting the counter across whole years (which <em>would</em> age
     * villagers), so it is intentionally not attempted here.</p>
     */
    @Nullable
    public static CalendarDate setToDate(MinecraftServer server, int displayYear, int month, int day) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = activeProfile(server);
        if (profile == null) return null;
        CalendarType type = CalendarTypes.resolveDriverFor(profile.id());
        if (type == null) return null;

        List<MonthDef> months = profile.months();
        List<LeapRule> rules = profile.leapRules();

        // Reject impossible dates against the target year's actual layout.
        LeapEngine.YearLayout layout = LeapEngine.layoutForYear(months, rules, displayYear);
        if (month < 1 || month > layout.months().size()) return null;
        if (day < 1 || day > layout.months().get(month - 1).days()) return null;

        long counter = data.worldDayCounter();

        // 1) Year via the offset only (counter untouched -> ages preserved).
        int newOffset = displayYear - type.compute(server, profile, counter, 0).year();

        // 2) Day-of-year via an in-year counter nudge. Staying inside the same
        //    year bucket keeps the displayed year (hence every age) unchanged.
        int curDayOfYear = type.compute(server, profile, counter, newOffset).dayOfYear();
        int targetDayOfYear = LeapEngine.daysBeforeMonth(months, rules, displayYear, month) + day;
        long newCounter = counter + (targetDayOfYear - curDayOfYear);

        data.setEpochYearOffset(newOffset);
        data.setWorldDayCounter(newCounter);
        rebroadcastAfterCalendarChange(server);
        return type.compute(server, profile, newCounter, newOffset);
    }

    /**
     * Snap the calendar to the server host's real-world current date (display
     * only, no aging; see {@link #setToDate}). On a Gregorian-shaped profile
     * this lands on the literal date. When the real date has no matching slot
     * (a real Feb 29 against a 28-day February with no leap rule, or a fantasy
     * month layout), it falls back to the same position in the year
     * (day-of-year), clamped to that year's length.
     */
    public static CalendarDate matchToday(MinecraftServer server) {
        LocalDate now = LocalDate.now();
        CalendarDate literal = setToDate(server, now.getYear(), now.getMonthValue(), now.getDayOfMonth());
        if (literal != null) return literal;

        // Positional fallback: same day-of-year, clamped to the profile's year.
        CalendarProfile profile = activeProfile(server);
        if (profile == null) return CalendarDate.UNKNOWN;
        LeapEngine.YearLayout layout = LeapEngine.layoutForYear(profile.months(), profile.leapRules(), now.getYear());
        int doy = Math.min(now.getDayOfYear(), Math.max(1, layout.daysPerYear()));
        int month = 1, day = 1, acc = 0;
        List<MonthDef> ms = layout.months();
        for (int i = 0; i < ms.size(); i++) {
            int d = ms.get(i).days();
            if (doy <= acc + d) { month = i + 1; day = doy - acc; break; }
            acc += d;
        }
        CalendarDate positional = setToDate(server, now.getYear(), month, day);
        return positional != null ? positional : CalendarDate.UNKNOWN;
    }

    /**
     * Currently effective calendar time mode: world override if set, otherwise
     * the {@code townstead.calendar.timeMode} config value. Returns
     * {@code "normal"} or {@code "real_clock"}.
     */
    public static String activeTimeMode(MinecraftServer server) {
        if (server != null) {
            String override = WorldCalendarSavedData.get(server).timeModeOverride();
            if (override != null) return override;
        }
        return TownsteadConfig.getCalendarTimeMode();
    }

    /** True when the effective time mode is {@code real_clock}. */
    public static boolean isRealClockMode(MinecraftServer server) {
        return "real_clock".equals(activeTimeMode(server));
    }

    public static void setTimeModeOverride(MinecraftServer server, @Nullable String mode) {
        WorldCalendarSavedData.get(server).setTimeModeOverride(mode);
        rebroadcastAfterCalendarChange(server);
    }

    public static void setProfileOverride(MinecraftServer server, @Nullable ResourceLocation id) {
        WorldCalendarSavedData.get(server).setActiveProfileOverride(id);
        rebroadcastAfterCalendarChange(server);
    }

    /**
     * Push fresh calendar + villager-life payloads to clients whenever the
     * displayed date shape changes. Villager birth dates are pre-resolved
     * server-side, so without this clients keep their cached snapshots from
     * the previous profile and the inspector shows stale dates.
     */
    private static void rebroadcastAfterCalendarChange(MinecraftServer server) {
        com.aetherianartificer.townstead.Townstead.townstead$broadcastCalendarSync(server);
        com.aetherianartificer.townstead.Townstead.townstead$broadcastAllVillagerLifeSync(server);
    }

    @Nullable
    public static CalendarProfile activeProfile(MinecraftServer server) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        ResourceLocation override = data.activeProfileOverride();
        if (override != null) {
            CalendarProfile p = resolveProfileById(override);
            if (p != null) return p;
        }
        String configChoice = TownsteadConfig.getCalendarProfile();
        if (configChoice != null && !configChoice.isBlank() && !configChoice.equalsIgnoreCase("auto")) {
            ResourceLocation parsed = tryParse(configChoice);
            if (parsed != null) {
                CalendarProfile p = resolveProfileById(parsed);
                if (p != null) return p;
            }
        }
        ResourceLocation autoId = CalendarCompat.resolveAutoId();
        CalendarProfile resolved = resolveProfileById(autoId);
        if (resolved != null) return resolved;
        return CalendarProfileRegistry.byId(CalendarCompat.vanillaId());
    }

    /**
     * Resolve a profile id by consulting registered
     * {@link DynamicProfileSource}s first (runtime-synthesized profiles that
     * have no JSON entry), then falling back to the data-pack-loaded
     * {@link CalendarProfileRegistry}.
     */
    @Nullable
    private static CalendarProfile resolveProfileById(ResourceLocation id) {
        CalendarProfile dynamic = DynamicProfileSources.tryBuild(id).orElse(null);
        if (dynamic != null) return dynamic;
        return CalendarProfileRegistry.byId(id);
    }

    // ---- Lifespan API ----

    /**
     * Returns the villager's date of birth in display-calendar terms, or
     * {@code null} if no birth has been stamped yet (pre-first-tick window
     * during initial spawn).
     */
    @Nullable
    public static CalendarDate birthdayOf(MinecraftServer server, VillagerEntityMCA villager) {
        CompoundTag life = VillagerLifeStamper.peek(villager);
        if (life == null) return null;
        return dateOf(server, LifeData.getBirthWorldDay(life));
    }

    /**
     * Age in display years (today's year minus birth year). May be 0 for
     * villagers born this year; negative is clamped to 0 defensively (would
     * only happen if the admin rewound the world day counter).
     */
    public static int ageYears(MinecraftServer server, VillagerEntityMCA villager) {
        CompoundTag life = VillagerLifeStamper.peek(villager);
        if (life == null) return 0;
        CalendarDate today = today(server);
        CalendarDate birth = dateOf(server, LifeData.getBirthWorldDay(life));
        return Math.max(0, today.year() - birth.year());
    }

    @Nullable
    public static CalendarDate establishmentOf(MinecraftServer server, ResourceLocation dimension, int villageId) {
        WorldCalendarSavedData.VillageKey key = new WorldCalendarSavedData.VillageKey(dimension, villageId);
        WorldCalendarSavedData.VillageBirth birth = WorldCalendarSavedData.get(server).getVillageBirth(key);
        if (birth == null) return null;
        return dateOf(server, birth.worldDay());
    }

    // ---- Internal ----

    @Nullable
    private static ResourceLocation tryParse(String s) {
        try {
            //? if >=1.21 {
            return ResourceLocation.parse(s);
            //?} else {
            /*return new ResourceLocation(s);
            *///?}
        } catch (Exception ex) {
            return null;
        }
    }
}
