package com.aetherianartificer.townstead.origin;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What happens when a {@link LifeStage}'s duration runs out. {@link #NEXT} is
 * the implicit default for any stage that isn't the last in the list; the last
 * stage defaults to {@link #STAY}.
 */
public enum StageEndAction {
    /** Advance to the next stage in the list. Implicit default for non-last stages. */
    NEXT,
    /** Stay in this stage forever. Default for the last stage. */
    STAY,
    /** Villager dies of old age when the stage expires. */
    DIE,
    /** Restart the stage list from the first entry (immortal cycle). */
    LOOP;

    @Nullable
    public static StageEndAction parse(String raw) {
        if (raw == null) return null;
        String norm = raw.trim().toLowerCase(Locale.ROOT);
        for (StageEndAction a : values()) {
            if (a.name().toLowerCase(Locale.ROOT).equals(norm)) return a;
        }
        return null;
    }
}
