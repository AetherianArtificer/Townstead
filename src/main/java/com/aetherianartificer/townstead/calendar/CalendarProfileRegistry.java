package com.aetherianartificer.townstead.calendar;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Server-side registry of data-pack-loaded {@link CalendarProfile}s. Populated
 * by {@link CalendarProfileJsonLoader} on each resource reload.
 *
 * Reads are unsynchronized; the loader rebuilds the map atomically. Per
 * reload-listener convention, no profile lookups happen during reload.
 */
public final class CalendarProfileRegistry {
    private static volatile Map<ResourceLocation, CalendarProfile> PROFILES = Map.of();

    private CalendarProfileRegistry() {}

    static void replaceAll(Map<ResourceLocation, CalendarProfile> next) {
        Map<ResourceLocation, CalendarProfile> copy = new LinkedHashMap<>(next);
        PROFILES = Map.copyOf(copy);
    }

    @Nullable
    public static CalendarProfile byId(ResourceLocation id) {
        return PROFILES.get(id);
    }

    public static List<CalendarProfile> all() {
        return List.copyOf(PROFILES.values());
    }

    public static List<String> idStrings() {
        return PROFILES.keySet().stream()
                .map(ResourceLocation::toString)
                .collect(Collectors.toUnmodifiableList());
    }

    public static boolean isEmpty() {
        return PROFILES.isEmpty();
    }
}
