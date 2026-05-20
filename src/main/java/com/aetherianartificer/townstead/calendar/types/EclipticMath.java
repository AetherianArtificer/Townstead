package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.compat.calendar.bridge.EclipticBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Vanilla structural math (Townstead's 365-day / 24-solar-term profile)
 * overlaid with Ecliptic Seasons' authoritative season state.
 *
 * Like {@link SereneMath}, the live season is queried only for today's date.
 * Historical dates carry a null season because Ecliptic's past state isn't
 * recoverable through its API.
 */
public class EclipticMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ecliptic_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "ecliptic_math");
    *///?}

    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        CalendarDate base = delegate.compute(server, profile, worldDay, epochYearOffset);
        if (server == null) return base;
        long today = WorldCalendarSavedData.get(server).worldDayCounter();
        if (worldDay != today) return base;
        ServerLevel overworld = server.overworld();
        if (overworld == null) return base;
        Optional<Season> live = EclipticBridge.currentSeason(overworld);
        if (live.isEmpty()) return base;
        return new CalendarDate(base.year(), base.monthIndex(), base.dayOfMonth(),
                base.dayOfWeek(), base.dayOfYear(), live.get());
    }
}
