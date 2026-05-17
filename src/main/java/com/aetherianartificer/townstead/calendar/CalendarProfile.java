package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Data-pack-loaded calendar profile. Carries only structural calendar data
 * (months, days per week, display name) plus a reference to a {@link CalendarType}
 * that owns the actual date-compute logic.
 *
 * Constructed by {@link CalendarProfileJsonLoader} from
 * {@code data/<ns>/townstead/calendar_profile/*.json}. The Java side never
 * hard-codes months; the bundled JSON under
 * {@code data/townstead/townstead/calendar_profile/} ships the defaults that
 * any other data pack can override.
 */
public record CalendarProfile(
        ResourceLocation id,
        Component displayName,
        ResourceLocation typeId,
        int daysPerWeek,
        List<MonthDef> months
) {
    public CalendarProfile {
        if (daysPerWeek <= 0) throw new IllegalArgumentException("daysPerWeek must be > 0");
        if (months == null) throw new IllegalArgumentException("months must not be null");
        months = List.copyOf(months);
    }

    /** Sum of {@code months[].days}. */
    public int daysPerYear() {
        int total = 0;
        for (MonthDef m : months) total += m.days();
        return total;
    }
}
