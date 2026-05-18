package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
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
        rebroadcastAfterCalendarChange(server);
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
        rebroadcastAfterCalendarChange(server);
    }

    public static void setProfileOverride(MinecraftServer server, @Nullable ResourceLocation id) {
        WorldCalendarSavedData.get(server).setActiveProfileOverride(id);
        rebroadcastAfterCalendarChange(server);
    }

    /**
     * Push fresh calendar + villager-life payloads to clients whenever the
     * displayed date shape changes. Villager birth dates are pre-resolved
     * server-side, so without this clients keep their cached snapshots from
     * the previous profile and the inspector shows stale dates.
     */
    private static void rebroadcastAfterCalendarChange(MinecraftServer server) {
        com.aetherianartificer.townstead.Townstead.townstead$broadcastCalendarSync(server);
        com.aetherianartificer.townstead.Townstead.townstead$broadcastAllVillagerLifeSync(server);
    }

    @Nullable
    public static CalendarProfile activeProfile(MinecraftServer server) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        ResourceLocation override = data.activeProfileOverride();
        if (override != null) {
            CalendarProfile p = resolveProfileById(override);
            if (p != null) return p;
        }
        String configChoice = TownsteadConfig.getCalendarProfile();
        if (configChoice != null && !configChoice.isBlank() && !configChoice.equalsIgnoreCase("auto")) {
            ResourceLocation parsed = tryParse(configChoice);
            if (parsed != null) {
                CalendarProfile p = resolveProfileById(parsed);
                if (p != null) return p;
            }
        }
        ResourceLocation autoId = CalendarCompat.resolveAutoId();
        CalendarProfile resolved = resolveProfileById(autoId);
        if (resolved != null) return resolved;
        return CalendarProfileRegistry.byId(CalendarCompat.vanillaId());
    }

    /**
     * Resolve a profile id by consulting registered
     * {@link DynamicProfileSource}s first (runtime-synthesized profiles that
     * have no JSON entry), then falling back to the data-pack-loaded
     * {@link CalendarProfileRegistry}.
     */
    @Nullable
    private static CalendarProfile resolveProfileById(ResourceLocation id) {
        CalendarProfile dynamic = DynamicProfileSources.tryBuild(id).orElse(null);
        if (dynamic != null) return dynamic;
        return CalendarProfileRegistry.byId(id);
    }

    // ---- Lifespan API ----

    /**
     * Returns the villager's date of birth in display-calendar terms, or
     * {@code null} if no birth has been stamped yet (pre-first-tick window
     * during initial spawn).
     */
    @Nullable
    public static CalendarDate birthdayOf(MinecraftServer server, VillagerEntityMCA villager) {
        CompoundTag life = VillagerLifeStamper.peek(villager);
        if (life == null) return null;
        return dateOf(server, LifeData.getBirthWorldDay(life));
    }

    /**
     * Age in display years (today's year minus birth year). May be 0 for
     * villagers born this year; negative is clamped to 0 defensively (would
     * only happen if the admin rewound the world day counter).
     */
    public static int ageYears(MinecraftServer server, VillagerEntityMCA villager) {
        CompoundTag life = VillagerLifeStamper.peek(villager);
        if (life == null) return 0;
        CalendarDate today = today(server);
        CalendarDate birth = dateOf(server, LifeData.getBirthWorldDay(life));
        return Math.max(0, today.year() - birth.year());
    }

    @Nullable
    public static CalendarDate establishmentOf(MinecraftServer server, ResourceLocation dimension, int villageId) {
        WorldCalendarSavedData.VillageKey key = new WorldCalendarSavedData.VillageKey(dimension, villageId);
        WorldCalendarSavedData.VillageBirth birth = WorldCalendarSavedData.get(server).getVillageBirth(key);
        if (birth == null) return null;
        return dateOf(server, birth.worldDay());
    }

    // ---- Internal ----

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
