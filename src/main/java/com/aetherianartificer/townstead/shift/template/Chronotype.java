package com.aetherianartificer.townstead.shift.template;

import net.conczin.mca.entity.ai.relationship.Personality;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Sleep-window chronotype band. Currently derived from MCA Personality; this
 * mapping is the intended migration target when the trait system lands. The
 * personality lookup is done by name string so it survives the personality
 * enum drift between 1.20.1 and 1.21.1.
 *
 * Sleep window (8 tick-hours; tick-hour 0 == 6 AM display):
 *   EARLY_BIRD 15..22       (9 PM .. 5 AM display, wakes 5 AM)
 *   STANDARD   17..24%24    (11 PM .. 7 AM display, wakes 7 AM)
 *   NIGHT_OWL  19..26%24    (1 AM .. 9 AM display, wakes 9 AM)
 * Townstead drives sleep via its own Sleep activity / bed-seeking, not vanilla
 * night-only beds, so windows that run into daytime (Night Owl) work fine.
 */
public enum Chronotype {
    EARLY_BIRD,
    STANDARD,
    NIGHT_OWL;

    private static final Map<String, Chronotype> BY_PERSONALITY_NAME = buildMap();

    private static Map<String, Chronotype> buildMap() {
        Map<String, Chronotype> m = new HashMap<>();
        // Early birds: cheerful, outgoing, calm-but-active types
        m.put("UPBEAT", EARLY_BIRD);
        m.put("PEACEFUL", EARLY_BIRD);
        m.put("EXTROVERTED", EARLY_BIRD);
        m.put("ANXIOUS", EARLY_BIRD);
        m.put("PEPPY", EARLY_BIRD);     // 1.20.1
        m.put("ATHLETIC", EARLY_BIRD);  // 1.20.1
        m.put("CONFIDENT", EARLY_BIRD); // 1.20.1

        // Standard: the default if unknown/UNASSIGNED
        m.put("FRIENDLY", STANDARD);
        m.put("INTROVERTED", STANDARD);
        m.put("ODD", STANDARD);
        m.put("SENSITIVE", STANDARD);
        m.put("PLAYFUL", STANDARD); // 1.21.1
        m.put("WITTY", STANDARD);   // 1.20.1
        m.put("UNASSIGNED", STANDARD);
        m.put("SHY", STANDARD); // 1.20.1

        // Night owls: brooding, lazy, or hedonistic types
        m.put("FLIRTY", NIGHT_OWL);
        m.put("GLOOMY", NIGHT_OWL);
        m.put("GREEDY", NIGHT_OWL);
        m.put("RELAXED", NIGHT_OWL);
        m.put("CRABBY", NIGHT_OWL);
        m.put("GRUMPY", NIGHT_OWL); // 1.20.1
        m.put("LAZY", NIGHT_OWL);   // 1.20.1
        return m;
    }

    public static Chronotype fromPersonality(@Nullable Personality p) {
        if (p == null) return STANDARD;
        return BY_PERSONALITY_NAME.getOrDefault(p.name(), STANDARD);
    }

    public static Chronotype fromName(@Nullable String name) {
        if (name == null) return STANDARD;
        String upper = name.toUpperCase(java.util.Locale.ROOT);
        // Backward-compat: existing user templates and saved villager NBT may
        // still carry the legacy "LARK" / "OWL" names.
        if (upper.equals("LARK")) return EARLY_BIRD;
        if (upper.equals("OWL")) return NIGHT_OWL;
        try {
            return Chronotype.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            return STANDARD;
        }
    }

    public int chipColor() {
        return switch (this) {
            case EARLY_BIRD -> 0xFFE8B040;
            case STANDARD -> 0xFFB0B0B0;
            case NIGHT_OWL -> 0xFF7A6CC4;
        };
    }

    public String translationKey() {
        return switch (this) {
            case EARLY_BIRD -> "townstead.chronotype.early_bird";
            case STANDARD -> "townstead.chronotype.standard";
            case NIGHT_OWL -> "townstead.chronotype.night_owl";
        };
    }

    /** Inclusive start tick-hour of the preferred sleep window (tick-hour 0 = 6 AM). */
    public int preferredSleepStart() {
        return switch (this) {
            case EARLY_BIRD -> 15;     // 9 PM (wakes 5 AM)
            case STANDARD -> 17;       // 11 PM (wakes 7 AM)
            case NIGHT_OWL -> 19;      // 1 AM (wakes 9 AM)
        };
    }

    /** Length in hours of the preferred sleep window. */
    public int preferredSleepLength() {
        return 8;
    }

    /** True if the given tick-hour (0..23) falls inside the preferred sleep window. */
    public boolean isPreferredSleepHour(int tickHour) {
        int start = preferredSleepStart();
        int len = preferredSleepLength();
        int h = Math.floorMod(tickHour, 24);
        for (int i = 0; i < len; i++) {
            if (((start + i) % 24) == h) return true;
        }
        return false;
    }
}
