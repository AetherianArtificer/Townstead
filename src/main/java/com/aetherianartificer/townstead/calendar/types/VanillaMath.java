package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.MonthDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;

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
        int dpy = profile.daysPerYear();
        if (dpy <= 0) return CalendarDate.UNKNOWN;
        int dpw = profile.daysPerWeek();
        long yearsElapsed = Math.floorDiv(worldDay, (long) dpy);
        int dayInYear = (int) Math.floorMod(worldDay, (long) dpy);

        int monthIndex = 1;
        int dayOfMonth = dayInYear + 1;
        int acc = 0;
        List<MonthDef> months = profile.months();
        for (int i = 0; i < months.size(); i++) {
            int days = months.get(i).days();
            if (dayInYear < acc + days) {
                monthIndex = i + 1;
                dayOfMonth = dayInYear - acc + 1;
                break;
            }
            acc += days;
        }

        int dayOfWeek = (int) Math.floorMod(worldDay, (long) dpw);
        int year = (int) (yearsElapsed + epochYearOffset);
        return new CalendarDate(year, monthIndex, dayOfMonth, dayOfWeek, dayInYear + 1, null);
    }
}
