package com.aetherianartificer.townstead.reaction;

import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of loaded reactions plus the matching {@link
 * TriggerIndex}. Replaced wholesale on every data-pack reload via {@link
 * #replaceAll}; readers always see a consistent view.
 */
public final class ReactionRegistry {
    private static volatile Map<ResourceLocation, Reaction> REACTIONS = Map.of();
    private static volatile TriggerIndex TRIGGERS = TriggerIndex.EMPTY;

    private ReactionRegistry() {}

    public static Optional<Reaction> get(ResourceLocation id) {
        return Optional.ofNullable(REACTIONS.get(id));
    }

    public static Collection<Reaction> all() {
        return REACTIONS.values();
    }

    public static int size() {
        return REACTIONS.size();
    }

    public static TriggerIndex triggers() {
        return TRIGGERS;
    }

    public static void replaceAll(Map<ResourceLocation, Reaction> next, TriggerIndex triggers) {
        REACTIONS = Map.copyOf(next == null ? Collections.emptyMap() : next);
        TRIGGERS = triggers == null ? TriggerIndex.EMPTY : triggers;
    }
}
