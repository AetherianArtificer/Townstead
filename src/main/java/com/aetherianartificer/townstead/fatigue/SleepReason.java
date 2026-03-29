package com.aetherianartificer.townstead.fatigue;

public enum SleepReason {
    NONE("none"),
    SCHEDULED_REST("scheduled_rest"),
    FATIGUE_REST("fatigue_rest"),
    EMERGENCY_COLLAPSE("emergency_collapse");

    private final String id;

    SleepReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SleepReason fromId(String id) {
        if (id == null || id.isBlank()) return NONE;
        for (SleepReason value : values()) {
            if (value.id.equals(id)) return value;
        }
        return NONE;
    }
}
