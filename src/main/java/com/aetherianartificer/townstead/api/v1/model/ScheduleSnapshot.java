package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;

/** A villager's shift schedule and what it is doing against it. Activities are {@code work}, {@code meet}, {@code rest}, {@code idle}. */
public record ScheduleSnapshot(
        String mode,
        String templateId,
        boolean customShifts,
        boolean nonDefaultCustomShifts,
        int currentTickHour,
        int currentDisplayHour,
        int currentShiftOrdinal,
        String currentActivity,
        String plannedActivity,
        String currentTemplateId,
        List<Integer> shifts,
        List<String> weekDayTemplates
) {
    public ScheduleSnapshot {
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
        weekDayTemplates = weekDayTemplates == null ? List.of() : List.copyOf(weekDayTemplates);
    }

    public boolean onSchedule() {
        return currentActivity != null && currentActivity.equals(plannedActivity);
    }
}
