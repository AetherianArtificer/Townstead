package com.aetherianartificer.townstead.api;

import java.util.List;

/** Serializable read-only schedule state for one Townstead villager. */
public record TownsteadScheduleSnapshot(
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
) {}
