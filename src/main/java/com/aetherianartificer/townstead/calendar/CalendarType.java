package com.aetherianartificer.townstead.calendar;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Date-compute strategy for a {@link CalendarProfile}. Hard-coded in Java
 * (compute logic isn't data-pack territory) and bound to a profile via
 * {@code CalendarProfile.typeId}.
 *
 * Implementations may ignore {@code worldDay} but must always return a valid
 * date for the given profile.
 */
public interface CalendarType {
    ResourceLocation id();

    CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset);

    /** Ticks per in-game day. Overridden only by profiles that stretch the day. */
    default long ticksPerDay() { return 24000L; }
}
