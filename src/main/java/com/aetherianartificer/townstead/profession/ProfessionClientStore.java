package com.aetherianartificer.townstead.profession;

import java.util.List;

/**
 * Client-side cache for the list of available profession IDs in the player's village.
 */
public final class ProfessionClientStore {
    private static volatile List<String> availableProfessions = List.of();

    private ProfessionClientStore() {}

    public static void set(List<String> professions) {
        availableProfessions = professions != null ? List.copyOf(professions) : List.of();
    }

    public static List<String> get() {
        return availableProfessions;
    }

    public static void clear() {
        availableProfessions = List.of();
    }
}
