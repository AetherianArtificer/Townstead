package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.time.LocalDate;

/**
 * Mirrors the host PC's wall-clock date. Year/month/day come from {@link LocalDate};
 * the profile's month list is treated as a *name source* only (month 1's display
 * name is read from the profile so packs can rename January however they like).
 *
 * Day count per month, year length, and day-of-week all come from
 * {@code LocalDate} so leap years and ISO weekdays are honored.
 *
 * For arbitrary {@code worldDay} values (e.g., villager date-of-birth lookups)
 * the result is computed by walking {@link LocalDate#now()} backward by
 * {@code todayWorldDay - worldDay} days, so an N-day-old villager shows as
 * N real days before today. Without this, DOB queries would all collapse to
 * today and ages would always be 0.
 *
 * {@code epochYearOffset} is intentionally ignored. The wall clock is the
 * authority on the year.
 */
public class LocaltimeMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "localtime_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "localtime_math");
    *///?}

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        long todayWorldDay = WorldCalendarSavedData.get(server).worldDayCounter();
        long daysBack = todayWorldDay - worldDay;
        LocalDate target = LocalDate.now().minusDays(daysBack);
        int dayOfWeek = target.getDayOfWeek().getValue() - 1; // ISO Mon=1 -> 0
        return new CalendarDate(
                target.getYear(),
                target.getMonthValue(),
                target.getDayOfMonth(),
                dayOfWeek,
                target.getDayOfYear(),
                null
        );
    }
}
