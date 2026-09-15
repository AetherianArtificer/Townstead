package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** Turns the calendar ticker's day rollover into one event per boundary crossed, finest first. */
public final class CalendarRollovers {
    private CalendarRollovers() {}

    public static void onDayRollover(MinecraftServer server, int daysAdvanced) {
        if (server == null || daysAdvanced <= 0) return;
        try {
            long today = TownsteadCalendar.worldDay(server);
            long yesterday = today - daysAdvanced;
            CalendarSnapshot before = CalendarImpl.snapshot(server, yesterday, TownsteadCalendar.dateOf(server, yesterday));
            CalendarSnapshot after = CalendarImpl.snapshot(server, today, TownsteadCalendar.today(server));
            List<CalendarRolloverEvent.Kind> kinds = new ArrayList<>();
            kinds.add(CalendarRolloverEvent.Kind.DAY);
            CalendarProfile profile = TownsteadCalendar.activeProfile(server);
            int daysPerWeek = profile == null ? 7 : Math.max(1, profile.daysPerWeek());
            if (Math.floorDiv(today, daysPerWeek) != Math.floorDiv(yesterday, daysPerWeek)) {
                kinds.add(CalendarRolloverEvent.Kind.WEEK);
            }
            if (before.month() != after.month() || before.year() != after.year()) {
                kinds.add(CalendarRolloverEvent.Kind.MONTH);
            }
            if (!before.season().equals(after.season()) && !after.season().isEmpty()) {
                kinds.add(CalendarRolloverEvent.Kind.SEASON);
            }
            if (before.year() != after.year()) {
                kinds.add(CalendarRolloverEvent.Kind.YEAR);
            }
            ApiEvents.calendarRollover(server, kinds, before, after, daysAdvanced);
        } catch (Throwable t) {
            ApiSupport.swallow("calendar.rollover", t);
        }
    }
}
