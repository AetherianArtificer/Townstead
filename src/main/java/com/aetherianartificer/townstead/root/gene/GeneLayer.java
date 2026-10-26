package com.aetherianartificer.townstead.root.gene;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What part of the carrier a gene describes, so a state can take one part away. A werewolf in
 * beast form suppresses {@code body}: the beast's body replaces the Root's, while how long they
 * live and what they eat stay theirs. Each gene type sets a default; a gene can set {@code "layer"}.
 */
public enum GeneLayer {
    /** Looks, size, movement, senses and abilities. */
    BODY,
    /** Lifespan, aging, fertility, reproduction, and what the carrier keeps or knows. */
    BEING,
    /** Diet, drink, sleep, temperament and allegiance. */
    HABITS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable GeneLayer parse(@Nullable String raw) {
        if (raw == null) return null;
        for (GeneLayer layer : values()) {
            if (layer.id().equals(raw.trim().toLowerCase(Locale.ROOT))) return layer;
        }
        return null;
    }
}
