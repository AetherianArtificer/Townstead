package com.aetherianartificer.townstead.origin;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * What members of an origin/heritage/ancestry are called. {@code adjective} is
 * optional (e.g. "Elven" vs the noun "Elf"); callers fall back to
 * {@code singular} when it is null.
 */
public record Demonym(Component singular, Component plural, @Nullable Component adjective) {
    public Component adjectiveOrSingular() {
        return adjective != null ? adjective : singular;
    }
}
