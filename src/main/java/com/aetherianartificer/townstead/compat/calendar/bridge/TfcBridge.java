package com.aetherianartificer.townstead.compat.calendar.bridge;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reflection-only bridge to TerraFirmaCraft's internal calendar. TFC's
 * calendar lives in {@code net.dries007.tfc.util.calendar} and is NOT a
 * public API, so this bridge probes multiple method names and signatures and
 * degrades silently on any failure.
 *
 * 1.20.x exposes {@code getTotalDays()}, {@code getTotalYears()}. 1.21.x
 * renamed several of these to {@code getTotalCalendarDays()} and similar.
 * The probe tries both name sets and remembers which one worked.
 *
 * TFC's {@code Season} enum uses {@code FALL} where Townstead uses {@code AUTUMN};
 * the name map handles that translation.
 *
 * Year base: TFC starts at year 1000 by convention. We return the raw year
 * from TFC, no rebase.
 */
public final class TfcBridge {

    public record TfcSnapshot(int year, int month, int dayOfMonth, int dayOfYear, Season season) {}

    private static volatile boolean probeAttempted = false;
    private static volatile boolean probeOk = false;
    private static Object serverCalendarInstance;
    private static Method getTotalDays;
    private static Method getTotalYears;
    private static Method getCalendarMonth;
    private static Method getDayOfMonth;
    private static Method getSeason;
    private static Map<String, Season> seasonByName;

    private TfcBridge() {}

    public static Optional<TfcSnapshot> currentSnapshot(ServerLevel level) {
        if (level == null) return Optional.empty();
        if (!ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)) return Optional.empty();
        if (!ensureProbe()) return Optional.empty();
        try {
            long totalDays = ((Number) getTotalDays.invoke(serverCalendarInstance)).longValue();
            long totalYears = ((Number) getTotalYears.invoke(serverCalendarInstance)).longValue();
            int year = (int) Math.max(0L, totalYears);
            int month = getCalendarMonth != null
                    ? ((Number) getCalendarMonth.invoke(serverCalendarInstance)).intValue() + 1
                    : 1;
            int dayOfMonth = getDayOfMonth != null
                    ? ((Number) getDayOfMonth.invoke(serverCalendarInstance)).intValue() + 1
                    : 1;
            int dayOfYear = (int) Math.floorMod(totalDays, 96L) + 1;
            Season season = null;
            if (getSeason != null) {
                Object s = getSeason.invoke(serverCalendarInstance);
                if (s instanceof Enum<?> e) {
                    season = seasonByName.get(e.name());
                }
            }
            return Optional.of(new TfcSnapshot(year, month, dayOfMonth, dayOfYear, season));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    private static synchronized boolean ensureProbe() {
        if (probeAttempted) return probeOk;
        probeAttempted = true;
        try {
            Class<?> calendars = Class.forName("net.dries007.tfc.util.calendar.Calendars");
            Field serverField = calendars.getField("SERVER");
            serverCalendarInstance = serverField.get(null);
            if (serverCalendarInstance == null) {
                probeOk = false;
                return false;
            }
            Class<?> calendarCls = serverCalendarInstance.getClass();
            getTotalDays = findFirstNoArg(calendarCls, "getTotalCalendarDays", "getTotalDays");
            getTotalYears = findFirstNoArg(calendarCls, "getTotalCalendarYears", "getTotalYears");
            getCalendarMonth = findFirstNoArgOptional(calendarCls, "getCalendarMonthOfYear", "getMonthOfYear");
            getDayOfMonth = findFirstNoArgOptional(calendarCls, "getCalendarDayOfMonth", "getDayOfMonth");
            getSeason = findFirstNoArgOptional(calendarCls, "getCalendarSeason", "getSeason");

            seasonByName = new HashMap<>();
            seasonByName.put("SPRING", Season.SPRING);
            seasonByName.put("SUMMER", Season.SUMMER);
            seasonByName.put("FALL", Season.AUTUMN);
            seasonByName.put("AUTUMN", Season.AUTUMN);
            seasonByName.put("WINTER", Season.WINTER);

            probeOk = getTotalDays != null && getTotalYears != null;
            if (!probeOk) {
                Townstead.LOGGER.info("[Calendar] TFC bridge incomplete: getTotalDays={}, getTotalYears={}",
                        getTotalDays != null, getTotalYears != null);
            }
        } catch (Throwable t) {
            Townstead.LOGGER.info("[Calendar] TFC bridge unavailable: {}", t.getMessage());
            probeOk = false;
        }
        return probeOk;
    }

    private static Method findFirstNoArg(Class<?> cls, String... names) {
        for (String n : names) {
            try {
                return cls.getMethod(n);
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    private static Method findFirstNoArgOptional(Class<?> cls, String... names) {
        return findFirstNoArg(cls, names);
    }
}
