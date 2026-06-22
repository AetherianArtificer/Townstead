package com.aetherianartificer.townstead.api;

/** Serializable read-only calendar state for commands, scripts, REST, and MCP adapters. */
public record TownsteadCalendarSnapshot(
        String profileId,
        long worldDay,
        int epochYearOffset,
        String timeMode,
        int year,
        int month,
        int day,
        int dayOfYear,
        int dayOfWeek,
        String season
) {}
