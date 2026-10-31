package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.LeapEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Drives the date off Townstead's monotonic worldDay counter and the profile's
 * month list. The default math for profiles where one in-game day equals one
 * calendar day.
 */
public class VanillaMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "vanilla_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "vanilla_math");
    *///?}

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        if (profile.months().isEmpty()) return CalendarDate.UNKNOWN;
        int dpw = profile.daysPerWeek();
        // A stretched profile starts its year where the season mod's year starts.
        long day = worldDay + com.aetherianartificer.townstead.calendar.CalendarStretch.shift(server, profile);
        LeapEngine.Split split = LeapEngine.splitWorldDay(profile.months(), profile.leapRules(),
                day, epochYearOffset);
        int dayOfWeek = (int) Math.floorMod(worldDay, (long) dpw);
        // A custom profile only labels days; a season mod still decides today's season.
        com.aetherianartificer.townstead.calendar.Season season = server != null
                && worldDay == com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.get(server).worldDayCounter()
                ? com.aetherianartificer.townstead.calendar.SeasonClock.season(server) : null;
        return new CalendarDate(split.year(), split.monthIndex(), split.dayOfMonth(),
                dayOfWeek, split.dayOfYear(), season);
    }
}
