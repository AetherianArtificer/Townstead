package com.aetherianartificer.townstead.shift.weekplan;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Named weekly rotation: an ordered list of shift-template ids, one per
 * weekday. Two flavors mirror {@code ShiftTemplate}: built-in (data pack JSON,
 * {@code builtIn=true}, namespace {@code townstead}) and user-defined (stored
 * in world SavedData, namespace {@code townstead_user}).
 *
 * <p>The list is length-agnostic. When applied to a villager under a calendar
 * with {@code daysPerWeek = N}, entries map to days 0..N-1; a shorter plan
 * leaves trailing days unset (daily fallback), a longer plan ignores extras.
 * Each entry is a template id ({@code "namespace:path"}); an empty string means
 * "leave this day on the daily fallback".
 */
public record WeekPlan(ResourceLocation id, String displayName, List<String> dayTemplates, boolean builtIn) {

    public static final String USER_NAMESPACE = "townstead_user";

    public WeekPlan {
        if (id == null) throw new IllegalArgumentException("id");
        if (displayName == null || displayName.isBlank()) displayName = id.getPath();
        dayTemplates = dayTemplates == null ? List.of() : List.copyOf(dayTemplates);
    }

    public boolean isUserPlan() {
        return USER_NAMESPACE.equals(id.getNamespace());
    }

    public List<String> copyDays() {
        return new ArrayList<>(dayTemplates);
    }
}
