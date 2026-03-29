package com.aetherianartificer.townstead.fatigue;

public enum SleepBlockReason {
    NONE("none"),
    NOT_RESTING("not_resting"),
    COLLAPSED("collapsed"),
    ATTACK_TARGET("attack_target"),
    COMBAT_THREAT("combat_threat"),
    INVALID_SLEEPING_BED("invalid_sleeping_bed"),
    BED_OCCUPIED("bed_occupied"),
    NO_BED_FOUND("no_bed_found"),
    BED_UNREACHABLE("bed_unreachable"),
    CLAIM_CONFLICT("claim_conflict");

    private final String id;

    SleepBlockReason(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SleepBlockReason fromId(String id) {
        if (id == null || id.isBlank()) return NONE;
        for (SleepBlockReason value : values()) {
            if (value.id.equals(id)) return value;
        }
        return NONE;
    }
}
