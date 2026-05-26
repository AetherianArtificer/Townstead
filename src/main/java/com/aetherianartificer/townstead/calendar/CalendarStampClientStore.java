package com.aetherianartificer.townstead.calendar;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Client-side mirror of the server's visible stamp set, replaced wholesale by
 * {@link CalendarStampSyncPayload}. Cleared on logout from {@code TownsteadClient}.
 */
public final class CalendarStampClientStore {

    private static volatile List<CalendarStamp> stamps = List.of();

    private CalendarStampClientStore() {}

    public static void setFrom(CalendarStampSyncPayload payload) {
        stamps = List.copyOf(payload.stamps());
    }

    public static List<CalendarStamp> get() {
        return stamps;
    }

    @Nullable
    public static CalendarStamp byId(UUID id) {
        for (CalendarStamp s : stamps) {
            if (s.id().equals(id)) return s;
        }
        return null;
    }

    public static void clear() {
        stamps = List.of();
    }
}
