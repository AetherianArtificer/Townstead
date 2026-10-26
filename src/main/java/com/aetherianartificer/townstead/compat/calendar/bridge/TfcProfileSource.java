package com.aetherianartificer.townstead.compat.calendar.bridge;

import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.DynamicProfileSource;
import com.aetherianartificer.townstead.calendar.MonthDef;
import com.aetherianartificer.townstead.calendar.WeekdayDef;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Synthesizes the {@code townstead_calendar:tfc} profile from TFC's live month length, so the grid
 * follows the player's {@code monthLength} config instead of the 8-day months in {@code tfc.json}.
 * Month and weekday names come from TFC's own keys ({@code tfc.enum.month.*},
 * {@code tfc.enum.day.*}) so they read exactly as TFC's calendar does, with English fallbacks for a
 * client missing TFC's assets. Returns empty when TFC is absent, leaving the static profile.
 */
public final class TfcProfileSource implements DynamicProfileSource {

    private static final String NAME_KEY = "calendar_profile.townstead_calendar.tfc.name";
    private static final String[] MONTHS = {
            "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december"
    };
    private static final String[] DAYS = {
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
    };

    @Override
    public Optional<CalendarProfile> tryBuild(ResourceLocation id) {
        if (!CalendarCompat.tfcId().equals(id)) return Optional.empty();
        if (!ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)) return Optional.empty();
        OptionalInt daysInMonth = TfcBridge.daysInMonth();
        if (daysInMonth.isEmpty()) return Optional.empty();
        int days = daysInMonth.getAsInt();

        List<MonthDef> months = new ArrayList<>(MONTHS.length);
        for (String month : MONTHS) {
            months.add(new MonthDef(Component.translatableWithFallback("tfc.enum.month." + month, english(month)), days));
        }
        List<WeekdayDef> weekdays = new ArrayList<>(DAYS.length);
        for (String day : DAYS) {
            weekdays.add(new WeekdayDef(
                    Component.translatableWithFallback("tfc.enum.day." + day, english(day)),
                    Component.translatable("calendar_profile.townstead_calendar.default.weekday." + day + ".short")));
        }
        return Optional.of(new CalendarProfile(id, Component.translatable(NAME_KEY), DAYS.length, months, null, weekdays));
    }

    @Override
    public Set<ResourceLocation> knownIds() {
        return ModCompat.isLoaded(CalendarCompat.TFC_MOD_ID)
                ? Set.of(CalendarCompat.tfcId())
                : Set.of();
    }

    private static String english(String key) {
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
    }
}
