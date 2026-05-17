package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
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
        LocalDate today = LocalDate.now();
        int dayOfWeek = today.getDayOfWeek().getValue() - 1; // ISO Mon=1 -> 0
        return new CalendarDate(
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                dayOfWeek,
                today.getDayOfYear(),
                null
        );
    }
}
