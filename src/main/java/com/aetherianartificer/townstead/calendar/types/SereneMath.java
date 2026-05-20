package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import com.aetherianartificer.townstead.calendar.Season;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.compat.calendar.bridge.SereneBridge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Vanilla structural math (Townstead's 96-day / 12-subseason / 6-day-week
 * profile) overlaid with Serene Seasons' authoritative season state. Per
 * [[feedback_seasons_only_from_mods]] the season MUST come from the mod at
 * query time, never invented by Townstead.
 *
 * Season is queried only when computing TODAY's date (i.e., when the
 * requested {@code worldDay} equals the world calendar's current day). For
 * historical lookups (DOB display, establishment dates) the season is left
 * null because the mod's past state isn't recoverable.
 */
public class SereneMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "serene_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "serene_math");
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
        Optional<Season> live = SereneBridge.currentSeason(overworld);
        if (live.isEmpty()) return base;
        return new CalendarDate(base.year(), base.monthIndex(), base.dayOfMonth(),
                base.dayOfWeek(), base.dayOfYear(), live.get());
    }
}
