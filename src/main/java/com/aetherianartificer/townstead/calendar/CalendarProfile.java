package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Data-pack-loaded calendar profile. Carries only structural calendar data
 * (months, days per week, display name, optional year suffix) plus a
 * reference to a {@link CalendarType} that owns the actual date-compute
 * logic.
 *
 * Constructed by {@link CalendarProfileJsonLoader} from
 * {@code data/<ns>/townstead/calendar_profile/*.json}. The Java side never
 * hard-codes months; the bundled JSON under
 * {@code data/townstead/townstead/calendar_profile/} ships the defaults that
 * any other data pack can override.
 *
 * {@code yearSuffix} is nullable; when null, callers that need a string
 * suffix should fall back to empty. Surfaced anywhere the displayed year
 * benefits from an era label in a HUD or chronicle entry; profiles set it
 * via the optional {@code year_suffix} JSON field (translatable + fallback)
 * or runtime synthesis.
 */
public record CalendarProfile(
        ResourceLocation id,
        Component displayName,
        ResourceLocation typeId,
        int daysPerWeek,
        List<MonthDef> months,
        @Nullable Component yearSuffix
) {
    public CalendarProfile {
        if (daysPerWeek <= 0) throw new IllegalArgumentException("daysPerWeek must be > 0");
        if (months == null) throw new IllegalArgumentException("months must not be null");
        months = List.copyOf(months);
    }

    /** Backwards-compatible constructor for callers that don't supply a suffix. */
    public CalendarProfile(
            ResourceLocation id,
            Component displayName,
            ResourceLocation typeId,
            int daysPerWeek,
            List<MonthDef> months
    ) {
        this(id, displayName, typeId, daysPerWeek, months, null);
    }

    /** Sum of {@code months[].days}. */
    public int daysPerYear() {
        int total = 0;
        for (MonthDef m : months) total += m.days();
        return total;
    }
}
