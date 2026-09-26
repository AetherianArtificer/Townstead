package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.CalendarApi;
import com.aetherianartificer.townstead.api.v1.model.CalendarProfileSnapshot;
import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.MonthDef;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WeekdayDef;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class CalendarImpl implements CalendarApi {

    private static final CalendarSnapshot EMPTY = new CalendarSnapshot("", 0L, 0, "", 0, 1, 1, 1, 0, "",
            "", 0, false, "", 0);

    @Override
    public CalendarSnapshot today(MinecraftServer server) {
        try {
            return snapshot(server, TownsteadCalendar.worldDay(server), TownsteadCalendar.today(server));
        } catch (Throwable t) {
            ApiSupport.swallow("calendar.today", t);
            return EMPTY;
        }
    }

    @Override
    public CalendarSnapshot dateOf(MinecraftServer server, long worldDay) {
        try {
            return snapshot(server, worldDay, TownsteadCalendar.dateOf(server, worldDay));
        } catch (Throwable t) {
            ApiSupport.swallow("calendar.dateOf", t);
            return EMPTY;
        }
    }

    @Override
    public CalendarProfileSnapshot profile(MinecraftServer server) {
        try {
            CalendarProfile profile = TownsteadCalendar.activeProfile(server);
            CalendarDate today = TownsteadCalendar.today(server);
            if (profile == null) return new CalendarProfileSnapshot("", today.year(), List.of(), List.of(), 0, false);
            List<CalendarProfileSnapshot.MonthInfo> months = new ArrayList<>();
            int index = 1;
            for (MonthDef month : profile.monthsForYear(today.year())) {
                months.add(new CalendarProfileSnapshot.MonthInfo(index++, month.commonName().getString(),
                        month.days(), month.days() == 1));
            }
            List<CalendarProfileSnapshot.WeekdayInfo> weekdays = new ArrayList<>();
            if (profile.weekdays() != null) {
                int day = 0;
                for (WeekdayDef weekday : profile.weekdays()) {
                    weekdays.add(new CalendarProfileSnapshot.WeekdayInfo(day++, weekday.longName().getString(),
                            weekday.shortName().getString()));
                }
            }
            return new CalendarProfileSnapshot(profile.id().toString(), today.year(), months, weekdays,
                    profile.daysInYear(today.year()), today.season() != null);
        } catch (Throwable t) {
            ApiSupport.swallow("calendar.profile", t);
            return new CalendarProfileSnapshot("", 0, List.of(), List.of(), 0, false);
        }
    }

    static CalendarSnapshot snapshot(MinecraftServer server, long worldDay, CalendarDate date) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        String monthName = "";
        int monthDays = 0;
        String weekdayName = "";
        int daysInYear = 0;
        if (profile != null) {
            List<MonthDef> months = profile.monthsForYear(date.year());
            int monthIndex = date.monthIndex() - 1;
            if (monthIndex >= 0 && monthIndex < months.size()) {
                MonthDef month = months.get(monthIndex);
                monthName = month.commonName().getString();
                monthDays = month.days();
            }
            List<WeekdayDef> weekdays = profile.weekdays();
            if (weekdays != null && date.dayOfWeek() >= 0 && date.dayOfWeek() < weekdays.size()) {
                weekdayName = weekdays.get(date.dayOfWeek()).longName().getString();
            }
            daysInYear = profile.daysInYear(date.year());
        }
        return new CalendarSnapshot(
                profile == null ? "" : profile.id().toString(),
                worldDay,
                data.epochYearOffset(),
                TownsteadCalendar.activeTimeMode(server),
                date.year(),
                date.monthIndex(),
                date.dayOfMonth(),
                date.dayOfYear(),
                date.dayOfWeek(),
                date.season() == null ? "" : date.season().name().toLowerCase(Locale.ROOT),
                monthName,
                monthDays,
                monthDays == 1,
                weekdayName,
                daysInYear);
    }
}
