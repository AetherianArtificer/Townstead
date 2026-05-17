package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side facade for everything calendar-related. Composes:
 *   - {@link WorldCalendarSavedData} (counter + offset + override)
 *   - {@link CalendarProfileRegistry} (data-pack-loaded profiles)
 *   - {@link CalendarTypes} (Java-side compute strategies)
 *   - {@link CalendarCompat} (auto-resolution from detected mods)
 *
 * Returns {@link CalendarDate#UNKNOWN} only as a defensive fallback if no
 * profile has loaded yet (pre-reload-listener call, e.g., very early server
 * startup). The reload listener fires before world data loads, so the normal
 * path always has profiles available.
 */
public final class TownsteadCalendar {
    private TownsteadCalendar() {}

    public static CalendarDate today(MinecraftServer server) {
        return dateOf(server, worldDay(server));
    }

    public static CalendarDate dateOf(MinecraftServer server, long worldDay) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = activeProfile(server);
        if (profile == null) return CalendarDate.UNKNOWN;
        CalendarType type = CalendarTypes.byId(profile.typeId());
        if (type == null) return CalendarDate.UNKNOWN;
        return type.compute(server, profile, worldDay, data.epochYearOffset());
    }

    public static long worldDay(MinecraftServer server) {
        return WorldCalendarSavedData.get(server).worldDayCounter();
    }

    public static int epochYearOffset(MinecraftServer server) {
        return WorldCalendarSavedData.get(server).epochYearOffset();
    }

    public static void setEpochYearOffset(MinecraftServer server, int offset) {
        WorldCalendarSavedData.get(server).setEpochYearOffset(offset);
    }

    /**
     * Rebase {@code epochYearOffset} so today's display year equals
     * {@code displayYear}. Counter is untouched, so all stored worldDay-based
     * dates (future Phase 2 DOBs / village establishments) shift only in
     * their displayed-year representation.
     */
    public static void rebaseToDisplayYear(MinecraftServer server, int displayYear) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = activeProfile(server);
        if (profile == null) return;
        CalendarType type = CalendarTypes.byId(profile.typeId());
        if (type == null) return;
        CalendarDate now = type.compute(server, profile, data.worldDayCounter(), 0);
        data.setEpochYearOffset(displayYear - now.year());
    }

    public static void setProfileOverride(MinecraftServer server, @Nullable ResourceLocation id) {
        WorldCalendarSavedData.get(server).setActiveProfileOverride(id);
    }

    @Nullable
    public static CalendarProfile activeProfile(MinecraftServer server) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        ResourceLocation override = data.activeProfileOverride();
        if (override != null) {
            CalendarProfile p = CalendarProfileRegistry.byId(override);
            if (p != null) return p;
        }
        String configChoice = TownsteadConfig.getCalendarProfile();
        if (configChoice != null && !configChoice.isBlank() && !configChoice.equalsIgnoreCase("auto")) {
            ResourceLocation parsed = tryParse(configChoice);
            if (parsed != null) {
                CalendarProfile p = CalendarProfileRegistry.byId(parsed);
                if (p != null) return p;
            }
        }
        ResourceLocation autoId = CalendarCompat.resolveAutoId();
        CalendarProfile resolved = CalendarProfileRegistry.byId(autoId);
        if (resolved != null) return resolved;
        return CalendarProfileRegistry.byId(CalendarCompat.vanillaId());
    }

    @Nullable
    private static ResourceLocation tryParse(String s) {
        try {
            //? if >=1.21 {
            return ResourceLocation.parse(s);
            //?} else {
            /*return new ResourceLocation(s);
            *///?}
        } catch (Exception ex) {
            return null;
        }
    }
}
