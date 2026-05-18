package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Same month math as {@link VanillaMath} but the day advances when the host's
 * system clock crosses midnight, not when in-game time does. Pairs with mods
 * that stretch one MC day across many real-world days.
 *
 * For arbitrary {@code worldDay} (e.g., villager date-of-birth) the input is
 * offset from {@code todayWorldDay} the same number of days back into the
 * real-time stream, so an N-day-old villager shows as N system-days before
 * "today" in this profile's frame. Otherwise DOB lookups would all collapse
 * to today and ages would be 0.
 */
public class RealtimeMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "realtime_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "realtime_math");
    *///?}

    private static final long MILLIS_PER_DAY = 86_400_000L;
    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        long systemDaysNow = System.currentTimeMillis() / MILLIS_PER_DAY;
        long todayWorldDay = WorldCalendarSavedData.get(server).worldDayCounter();
        long daysBack = todayWorldDay - worldDay;
        long effective = systemDaysNow - daysBack;
        return delegate.compute(server, profile, effective, epochYearOffset);
    }
}
