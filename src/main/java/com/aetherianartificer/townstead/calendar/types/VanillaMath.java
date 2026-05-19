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
        LeapEngine.Split split = LeapEngine.splitWorldDay(profile.months(), profile.leapRules(),
                worldDay, epochYearOffset);
        int dayOfWeek = (int) Math.floorMod(worldDay, (long) dpw);
        return new CalendarDate(split.year(), split.monthIndex(), split.dayOfMonth(),
                dayOfWeek, split.dayOfYear(), null);
    }
}
