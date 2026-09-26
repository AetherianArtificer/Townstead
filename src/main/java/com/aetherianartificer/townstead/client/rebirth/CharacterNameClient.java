package com.aetherianartificer.townstead.client.rebirth;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/** Reborn players' current names, as the server last sent them. */
public final class CharacterNameClient {
    private CharacterNameClient() {}

    private static volatile Map<UUID, String> names = Map.of();

    public static void set(Map<UUID, String> next) {
        names = Map.copyOf(next);
    }

    public static @Nullable String get(UUID player) {
        return names.get(player);
    }

    public static void clear() {
        names = Map.of();
    }
}
