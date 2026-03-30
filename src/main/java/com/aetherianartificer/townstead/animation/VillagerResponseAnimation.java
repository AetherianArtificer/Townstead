package com.aetherianartificer.townstead.animation;

import java.util.Locale;

public enum VillagerResponseAnimation {
    NEUTRAL_IDLE("neutral_idle", 40),
    WAVE_BACK("wave_back", 28),
    CAUTIOUS_ACK("cautious_ack", 26),
    APPLAUD("applaud", 32),
    BASHFUL("bashful", 30),
    COMFORT("comfort", 34),
    AWKWARD("awkward", 30),
    ACKNOWLEDGE("acknowledge", 18),
    PUZZLED("puzzled", 26),
    AMUSED("amused", 26),
    STARTLED("startled", 18);

    private final String id;
    private final int durationTicks;

    VillagerResponseAnimation(String id, int durationTicks) {
        this.id = id;
        this.durationTicks = durationTicks;
    }

    public String id() {
        return id;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public static VillagerResponseAnimation fromId(String id) {
        if (id == null || id.isBlank()) return null;

        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (VillagerResponseAnimation animation : values()) {
            if (animation.id.equals(normalized)) return animation;
        }

        return null;
    }
}
