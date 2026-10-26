package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.ability.Ability;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * How a {@code slow_fall} bearer comes down. Set by the {@code style} field on the gene.
 *
 * <ul>
 * <li>{@code float}: every descent is slowed, a short hop included (the default).</li>
 * <li>{@code parachute}: a normal fall until the drop passes {@link ParachutePhysics#OPEN_DROP},
 * then a slow, steady descent until landing. Hops and steps stay normal.</li>
 * </ul>
 */
public enum FallStyle {
    FLOAT("float"),
    PARACHUTE("parachute");

    private final String key;

    FallStyle(String key) {
        this.key = key;
    }

    public String key() { return key; }

    /** Fall styles ride on {@code slow_fall}. */
    public static boolean appliesTo(Ability ability) {
        return ability == Ability.SLOW_FALL;
    }

    @Nullable
    public static FallStyle byKey(@Nullable String key) {
        if (key == null) return null;
        String needle = key.toLowerCase(Locale.ROOT);
        for (FallStyle style : values()) {
            if (style.key.equals(needle)) return style;
        }
        return null;
    }
}
