package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.ability.Ability;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How a swimmer moves through water. Set by the {@code style} field on a {@code swimming}
 * or {@code water_breathing} gene; {@link SwimProfile} infers it when the field is absent.
 *
 * <ul>
 * <li>{@code swimSpeed}: blocks per tick swimming, for weighing a swim against a walk.</li>
 * <li>{@code depth}: how far below the surface the swimmer likes to travel; 0 keeps to
 * the surface.</li>
 * <li>{@code waterCost}: route cost of a block of water against a block of land; under
 * 1 the swimmer goes out of its way to stay in water.</li>
 * <li>{@code dwells}: free time is spent in the water, not on land.</li>
 * </ul>
 */
public enum SwimStyle {
    PADDLE("paddle", 0.12, 0.0, 1.5, false),
    DIVE("dive", 0.2, 2.0, 1.0, false),
    FISH("fish", 0.26, 3.0, 0.6, true);

    private final String key;
    private final double swimSpeed;
    private final double depth;
    private final double waterCost;
    private final boolean dwells;

    SwimStyle(String key, double swimSpeed, double depth, double waterCost, boolean dwells) {
        this.key = key;
        this.swimSpeed = swimSpeed;
        this.depth = depth;
        this.waterCost = waterCost;
        this.dwells = dwells;
    }

    public String key() { return key; }
    public double swimSpeed() { return swimSpeed; }
    public double depth() { return depth; }
    public double waterCost() { return waterCost; }
    public boolean dwells() { return dwells; }

    /** Swim styles ride on the two water abilities. */
    public static boolean appliesTo(Ability ability) {
        return ability == Ability.SWIMMING || ability == Ability.WATER_BREATHING;
    }

    @Nullable
    public static SwimStyle byKey(@Nullable String key) {
        if (key == null) return null;
        String needle = key.toLowerCase(Locale.ROOT);
        for (SwimStyle style : values()) {
            if (style.key.equals(needle)) return style;
        }
        return null;
    }
}
