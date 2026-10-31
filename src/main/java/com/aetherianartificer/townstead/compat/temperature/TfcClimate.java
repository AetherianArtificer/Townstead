package com.aetherianartificer.townstead.compat.temperature;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import com.aetherianartificer.townstead.compat.calendar.bridge.TfcBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.OptionalDouble;

/**
 * TerraFirmaCraft's own climate: the yearly average and the instant temperature at a calendar
 * tick. Both include TFC's elevation; the instant one also has TFC's months, its noon-warm hour
 * term and its day-to-day noise. Worlds on TFC's biome-based fallback model are left to Townstead.
 * {@code getInstantTemperature} was {@code getTemperature} on 1.20, and the 1.20 average has no
 * elevation, so that version adds it through the model's monthly average at a zero month factor.
 */
public final class TfcClimate {
    static final long DAY = 24000L;
    /** TFC calendar ticks start at 06:00, vanilla day time at 0. */
    static final long CALENDAR_OFFSET = 6000L;

    private static boolean initialized;
    private static Method model, instant, average, calendarTicks, daysInMonth, monthlyAverage;
    private static Object calendar;
    private static boolean warned;

    private TfcClimate() {}

    /** TFC's own climate model runs in this level. */
    public static boolean applies(Level level) {
        if (level == null || !ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)) return false;
        init();
        if (instant == null) return false;
        try {
            Object m = model.invoke(null, level);
            return m != null && !m.getClass().getSimpleName().equals("BiomeBasedClimateModel");
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    /** TFC's yearly average at the position, with elevation. */
    public static OptionalDouble average(Level level, BlockPos pos) {
        try {
            float avg = ((Number) average.invoke(null, level, pos)).floatValue();
            if (monthlyAverage != null) {
                Object m = model.invoke(null, level);
                if (monthlyAverage.getDeclaringClass().isInstance(m))
                    avg = ((Number) monthlyAverage.invoke(m, pos.getZ(), pos.getY(), avg, 0f)).floatValue();
            }
            return OptionalDouble.of(avg);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return OptionalDouble.empty();
        }
    }

    /** TFC's temperature at the position at a calendar tick. */
    public static OptionalDouble instant(Level level, BlockPos pos, long tick) {
        try {
            int month = ((Number) daysInMonth.invoke(calendar)).intValue();
            return OptionalDouble.of(((Number) instant.invoke(null, level, pos, tick, month)).floatValue());
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return OptionalDouble.empty();
        }
    }

    /** The mean of a calendar day: midnight and noon cancel TFC's hour term and keep the day's noise. */
    public static OptionalDouble dayMean(Level level, BlockPos pos, long tick) {
        long[] ticks = dayMeanTicks(tick);
        var midnight = instant(level, pos, ticks[0]);
        var noon = instant(level, pos, ticks[1]);
        if (midnight.isEmpty() || noon.isEmpty()) return OptionalDouble.empty();
        return OptionalDouble.of((midnight.getAsDouble() + noon.getAsDouble()) / 2);
    }

    public static long now() {
        try {
            return ((Number) calendarTicks.invoke(calendar)).longValue();
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return 0L;
        }
    }

    /** Calendar ticks in a TFC year. */
    public static long yearTicks() {
        return 12L * TfcBridge.daysInMonth().orElse(30) * DAY;
    }

    /** The calendar tick for a season-clock phase and a vanilla time of day, in the current year. */
    public static long tickAt(double phase, long dayTicks) {
        int month = TfcBridge.daysInMonth().orElse(30);
        return tickAt(now(), month, TfcBridge.springStartMonth(), phase, dayTicks);
    }

    static long tickAt(long now, int daysInMonth, int springMonth, double phase, long dayTicks) {
        long year = 12L * daysInMonth * DAY;
        long spring = (long) springMonth * daysInMonth * DAY;
        long yearStart = Math.floorDiv(now - spring, year) * year + spring;
        long day = (long) Math.floor(Math.floorMod((long) Math.floor(phase * 12 * daysInMonth), 12L * daysInMonth));
        return yearStart + day * DAY + Math.floorMod(dayTicks + CALENDAR_OFFSET, DAY);
    }

    /** Midnight and noon of the calendar day holding {@code tick}. */
    static long[] dayMeanTicks(long tick) {
        long start = Math.floorDiv(tick, DAY) * DAY;
        return new long[]{start, start + DAY / 2};
    }

    private static synchronized void init() {
        if (initialized) return;
        initialized = true;
        try {
            Class<?> climate = Class.forName("net.dries007.tfc.util.climate.Climate");
            model = find(climate, "get", Level.class);
            if (model == null) model = climate.getMethod("model", Level.class);
            average = climate.getMethod("getAverageTemperature", Level.class, BlockPos.class);
            instant = find(climate, "getInstantTemperature", Level.class, BlockPos.class, long.class, int.class);
            if (instant == null) {
                instant = climate.getMethod("getTemperature", Level.class, BlockPos.class, long.class, int.class);
                Class<?> overworld = Class.forName("net.dries007.tfc.util.climate.OverworldClimateModel");
                monthlyAverage = find(overworld, "getAverageMonthlyTemperature", int.class, int.class, float.class, float.class);
            }
            calendar = Class.forName("net.dries007.tfc.util.calendar.Calendars").getField("SERVER").get(null);
            calendarTicks = calendar.getClass().getMethod("getCalendarTicks");
            daysInMonth = calendar.getClass().getMethod("getCalendarDaysInMonth");
        } catch (Throwable t) {
            instant = null;
            Townstead.LOGGER.info("[Climate] TFC climate unavailable: {}", t.toString());
        }
    }

    private static Method find(Class<?> owner, String name, Class<?>... args) {
        try {
            return owner.getMethod(name, args);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static void warn(Throwable t) {
        if (warned) return;
        warned = true;
        Townstead.LOGGER.warn("[Climate] TFC climate read failed: {}", t.toString());
    }
}
