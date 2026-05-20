package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;

/**
 * A named weekday within a {@link CalendarProfile}. Long form is used in
 * date strings ("Cwynday, Day 17 of …"), short form in calendar-grid column
 * headers ("Cwyn").
 *
 * Optional on profiles: when a profile doesn't declare weekdays, the
 * calendar UI falls back to numeric labels (1, 2, 3…) and date formatters
 * omit the weekday segment.
 */
public record WeekdayDef(Component longName, Component shortName) {}
