package com.aetherianartificer.townstead.compat.calendar.bridge;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Reflection-only bridge to TerraFirmaCraft's server calendar ({@code Calendars.SERVER}), which is
 * not a public API. Only two readings are taken, both present under the same names on 1.20.x and
 * 1.21.x: {@code getTotalCalendarDays()} (a monotonic day count) and
 * {@code getCalendarDaysInMonth()} (the configured month length). TFC derives everything else
 * from those two (month, day of month, year from 1000, weekday), and so does {@link
 * com.aetherianartificer.townstead.calendar.types.TfcMath}. The derived getters were renamed in
 * 1.21 ({@code getTotalCalendarYears} became {@code getCalendarYear}, months became hemispheral),
 * which is why they are not read.
 */
public final class TfcBridge {

    /** Today in TFC terms: absolute calendar day, month length, and the season of today's month. */
    public record TfcState(long absDay, int daysInMonth, @Nullable Season season) {}

    private static final Map<String, Season> SEASONS = Map.of(
            "SPRING", Season.SPRING,
            "SUMMER", Season.SUMMER,
            "FALL", Season.AUTUMN,
            "AUTUMN", Season.AUTUMN,
            "WINTER", Season.WINTER);

    private static volatile boolean probeAttempted = false;
    private static volatile boolean probeOk = false;
    private static Object serverCalendar;
    private static Method getTotalCalendarDays;
    private static Method getCalendarDaysInMonth;
    @Nullable private static Object[] months;
    @Nullable private static Method monthSeason;

    private TfcBridge() {}

    public static Optional<TfcState> currentState(ServerLevel level) {
        if (level == null) return Optional.empty();
        if (!ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)) return Optional.empty();
        if (!ensureProbe()) return Optional.empty();
        try {
            long absDay = ((Number) getTotalCalendarDays.invoke(serverCalendar)).longValue();
            int daysInMonth = ((Number) getCalendarDaysInMonth.invoke(serverCalendar)).intValue();
            if (daysInMonth <= 0) return Optional.empty();
            int month0 = (int) Math.floorMod(Math.floorDiv(absDay, (long) daysInMonth), 12L);
            return Optional.of(new TfcState(absDay, daysInMonth, seasonOf(month0)));
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** TFC's configured month length, for shaping the profile. */
    public static OptionalInt daysInMonth() {
        if (!ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)) return OptionalInt.empty();
        if (!ensureProbe()) return OptionalInt.empty();
        try {
            int days = ((Number) getCalendarDaysInMonth.invoke(serverCalendar)).intValue();
            return days > 0 ? OptionalInt.of(days) : OptionalInt.empty();
        } catch (Throwable t) {
            return OptionalInt.empty();
        }
    }

    @Nullable
    private static Season seasonOf(int month0) {
        if (months == null || monthSeason == null || month0 < 0 || month0 >= months.length) return null;
        try {
            Object season = monthSeason.invoke(months[month0]);
            return season instanceof Enum<?> e ? SEASONS.get(e.name()) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static synchronized boolean ensureProbe() {
        if (probeAttempted) return probeOk;
        probeAttempted = true;
        try {
            Class<?> calendars = Class.forName("net.dries007.tfc.util.calendar.Calendars");
            serverCalendar = calendars.getField("SERVER").get(null);
            if (serverCalendar == null) return probeOk = false;
            Class<?> calendarCls = serverCalendar.getClass();
            getTotalCalendarDays = calendarCls.getMethod("getTotalCalendarDays");
            getCalendarDaysInMonth = calendarCls.getMethod("getCalendarDaysInMonth");
            try {
                Class<?> month = Class.forName("net.dries007.tfc.util.calendar.Month");
                months = month.getEnumConstants();
                monthSeason = month.getMethod("getSeason");
            } catch (Throwable ignored) {
                // Season is optional; dates still match without it.
            }
            probeOk = true;
        } catch (Throwable t) {
            Townstead.LOGGER.info("[Calendar] TFC bridge unavailable: {}", t.toString());
            probeOk = false;
        }
        return probeOk;
    }
}
