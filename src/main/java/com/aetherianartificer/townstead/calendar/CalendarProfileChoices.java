package com.aetherianartificer.townstead.calendar;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Composes the list of choices a calendar-profile picker UI should offer.
 * Combines:
 *
 * <ul>
 *   <li>{@code "auto"} — special id meaning "let {@link com.aetherianartificer.townstead.compat.calendar.CalendarCompat#resolveAutoId} decide based on detected mods."</li>
 *   <li>JSON-loaded ids from {@link CalendarProfileRegistry}.</li>
 *   <li>Currently-available ids from {@link DynamicProfileSources#listKnownIds} (e.g., bridged calendar mods).</li>
 * </ul>
 *
 * Duplicates are removed (a dynamic source advertising an id that also has a
 * JSON file is listed once). Order: {@code "auto"} first, then registry
 * insertion order, then dynamic source insertion order — keeps stable layout
 * across reloads while letting custom data packs surface alongside built-in
 * choices.
 */
public final class CalendarProfileChoices {
    public static final String AUTO = "auto";

    private CalendarProfileChoices() {}

    /** All profile id strings the picker should show, with {@code "auto"} first. */
    public static List<String> listAll() {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        ids.add(AUTO);
        for (CalendarProfile profile : CalendarProfileRegistry.all()) {
            ids.add(profile.id().toString());
        }
        for (ResourceLocation rl : DynamicProfileSources.listKnownIds()) {
            ids.add(rl.toString());
        }
        return new ArrayList<>(ids);
    }
}
