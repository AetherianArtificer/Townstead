package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;

/**
 * One month within a {@link CalendarProfile}. {@code days} is the number of
 * calendar days in this month; the sum across a profile's month list defines
 * its year length. {@code commonName} is the displayed name as an MC text
 * component (typically a {@code translate} component from a data pack).
 */
public record MonthDef(Component commonName, int days) {
    public MonthDef {
        if (days <= 0) throw new IllegalArgumentException("MonthDef days must be > 0");
    }
}
