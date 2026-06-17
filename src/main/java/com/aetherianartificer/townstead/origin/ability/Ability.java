package com.aetherianartificer.townstead.origin.ability;

import java.util.Locale;

/**
 * The innate, passive abilities a gene can grant (the heritable subset of Apoli's
 * ability powers). Active/class abilities are out of scope here and handled by the
 * professions system. {@code playerOnly} abilities are heritable data but only
 * applied at runtime to players (they are nonsensical or disruptive on AI villagers).
 */
public enum Ability {
    CLIMBING("climbing", false),
    WATER_BREATHING("water_breathing", false),
    FIRE_IMMUNITY("fire_immunity", false),
    NIGHT_VISION("night_vision", false),
    SLOW_FALL("slow_fall", false),
    LAVA_VISION("lava_vision", false),
    INVISIBILITY("invisibility", false),
    SWIMMING("swimming", false),
    WALK_ON_FLUID("walk_on_fluid", false),
    IGNORE_WATER("ignore_water", false),
    HOVER("hover", false),
    SPRINTING("sprinting", false),
    AERIAL_AFFINITY("aerial_affinity", true),
    GROUNDED("grounded", true),
    ELYTRA_FLIGHT("elytra_flight", true),
    CREATIVE_FLIGHT("creative_flight", true),
    PHASING("phasing", true);

    private final String key;
    private final boolean playerOnly;

    Ability(String key, boolean playerOnly) {
        this.key = key;
        this.playerOnly = playerOnly;
    }

    public String key() {
        return key;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    /** Resolve an ability by its JSON key, or {@code null} if unknown. */
    public static Ability byKey(String key) {
        if (key == null) return null;
        String needle = key.toLowerCase(Locale.ROOT);
        for (Ability ability : values()) {
            if (ability.key.equals(needle)) return ability;
        }
        return null;
    }
}
