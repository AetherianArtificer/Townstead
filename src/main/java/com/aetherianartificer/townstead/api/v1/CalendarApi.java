package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.CalendarProfileSnapshot;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import net.minecraft.server.MinecraftServer;

/** The world calendar: today's date, any other day's date, and the active profile's shape. */
public interface CalendarApi {

    CalendarSnapshot today(MinecraftServer server);

    CalendarSnapshot dateOf(MinecraftServer server, long worldDay);

    /** Months, weekdays and seasons of the active profile. One-day months are the festivals. */
    CalendarProfileSnapshot profile(MinecraftServer server);
}
