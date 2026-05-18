package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for constructing a {@link CalendarProfile} from raw values — the
 * shape that reflection-based {@link DynamicProfileSource}s typically have
 * after pulling data out of a third-party mod.
 *
 * Wraps the bookkeeping (blank-name fallback, Component construction,
 * uniform daysPerMonth replication across months) so each bridge stays
 * focused on its mod's API surface.
 */
public final class CalendarProfileBuilder {
    private CalendarProfileBuilder() {}

    /**
     * Build a profile with uniform days per month. Used when the source mod
     * only exposes a single {@code daysPerMonth} value (e.g., Timeline's
     * {@code CalendarDefinition} model).
     *
     * @param id            profile id (e.g., {@code townstead_calendar:default})
     * @param displayKey    lang key for the profile's display name
     * @param displayFallback fallback text if no translation is loaded
     * @param typeId        which {@link CalendarType} drives date math
     *                      (typically {@code VanillaMath.ID})
     * @param daysPerWeek   weekday cycle length
     * @param monthNames    array of month display names (literal text);
     *                      empty / null name slots get "Month" placeholder
     * @param daysPerMonth  uniform days for every month (must be &gt; 0)
     * @param yearSuffix    optional era suffix; null / blank → no suffix
     */
    public static CalendarProfile uniform(
            ResourceLocation id,
            String displayKey,
            String displayFallback,
            ResourceLocation typeId,
            int daysPerWeek,
            String[] monthNames,
            int daysPerMonth,
            @Nullable String yearSuffix
    ) {
        if (monthNames == null || monthNames.length == 0 || daysPerMonth <= 0) {
            throw new IllegalArgumentException("monthNames empty or daysPerMonth <= 0");
        }
        List<MonthDef> months = new ArrayList<>(monthNames.length);
        for (String name : monthNames) {
            String safe = (name == null || name.isBlank()) ? "Month" : name;
            months.add(new MonthDef(Component.literal(safe), daysPerMonth));
        }
        Component displayName = Component.translatableWithFallback(displayKey, displayFallback);
        Component suffixComponent = (yearSuffix == null || yearSuffix.isBlank())
                ? null : Component.literal(yearSuffix);
        return new CalendarProfile(id, displayName, typeId, daysPerWeek, months, suffixComponent);
    }
}
