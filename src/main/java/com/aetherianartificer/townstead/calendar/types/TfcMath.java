package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.compat.calendar.bridge.TfcBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Pulls full date information from TFC's internal calendar when available.
 * Unlike {@link SereneMath} and {@link EclipticMath}, this profile uses
 * TFC's year/month/day/season rather than Townstead's structural math,
 * because TFC players install the mod expecting to see TFC dates and TFC
 * authors the entire calendar (year 1000+, 96-day year, 8-day week, four
 * seasons).
 *
 * Live data is queried only for today's date. Historical lookups (DOB,
 * village establishment) fall back to the vanilla structural math against
 * the {@code townstead_calendar:tfc} profile — accurate enough as "X years ago in
 * TFC terms" without requiring a snapshot of TFC's past state.
 *
 * If TFC isn't loaded or the reflection probe fails, the bridge returns
 * empty and the profile behaves identically to the Phase 1 stub.
 */
public class TfcMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "tfc_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "tfc_math");
    *///?}

    private static final int TFC_BASE_YEAR = 1000;
    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        if (server != null) {
            long today = WorldCalendarSavedData.get(server).worldDayCounter();
            if (worldDay == today) {
                ServerLevel overworld = server.overworld();
                if (overworld != null) {
                    Optional<TfcBridge.TfcSnapshot> snap = TfcBridge.currentSnapshot(overworld);
                    if (snap.isPresent()) {
                        TfcBridge.TfcSnapshot s = snap.get();
                        int dpw = profile.daysPerWeek();
                        int dow = (int) Math.floorMod((long) s.dayOfYear() - 1, (long) dpw);
                        return new CalendarDate(s.year(), s.month(), s.dayOfMonth(), dow, s.dayOfYear(), s.season());
                    }
                }
            }
        }
        // Fallback: structural math with TFC's year base baked in.
        return delegate.compute(server, profile, worldDay, epochYearOffset + TFC_BASE_YEAR);
    }
}
