package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.compat.calendar.bridge.TfcBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * TFC-authoritative calendar math, the same shape as {@link EclipticMath}. TFC keeps a monotonic
 * absolute calendar day, and derives its whole date from it and the configured month length:
 * 12 months of {@code daysInMonth} days, years counted from 1000, weekday = absolute day mod 7
 * starting on Monday. Townstead applies that same arithmetic, so today matches TFC's own calendar
 * exactly.
 *
 * <p>Historical dates (villager DOB, village founding) extrapolate backward from TFC's day by the
 * worldDay delta. Season is only reported for today. Falls back to {@link VanillaMath} with TFC's
 * year base when TFC's calendar can't be read.</p>
 */
public class TfcMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "tfc_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "tfc_math");
    *///?}

    private static final int TFC_BASE_YEAR = 1000;
    private static final int MONTHS_PER_YEAR = 12;
    private static final int TFC_DAYS_PER_WEEK = 7;
    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        if (server != null) {
            ServerLevel overworld = server.overworld();
            Optional<TfcBridge.TfcState> live = overworld == null ? Optional.empty() : TfcBridge.currentState(overworld);
            if (live.isPresent()) {
                TfcBridge.TfcState s = live.get();
                int daysInMonth = s.daysInMonth();
                long daysPerYear = (long) MONTHS_PER_YEAR * daysInMonth;
                long counterToday = WorldCalendarSavedData.get(server).worldDayCounter();
                long abs = s.absDay() - (counterToday - worldDay);

                int year = TFC_BASE_YEAR + (int) Math.floorDiv(abs, daysPerYear) + epochYearOffset;
                int dayOfYear0 = (int) Math.floorMod(abs, daysPerYear);
                int month0 = dayOfYear0 / daysInMonth;
                int dayOfMonth = dayOfYear0 - month0 * daysInMonth + 1;
                int dayOfWeek = (int) Math.floorMod(abs, (long) TFC_DAYS_PER_WEEK);
                Season season = worldDay == counterToday ? s.season() : null;
                return new CalendarDate(year, month0 + 1, dayOfMonth, dayOfWeek, dayOfYear0 + 1, season);
            }
        }
        return delegate.compute(server, profile, worldDay, epochYearOffset + TFC_BASE_YEAR);
    }
}
