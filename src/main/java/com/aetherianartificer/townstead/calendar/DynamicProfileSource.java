package com.aetherianartificer.townstead.calendar;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.Set;

/**
 * Pluggable provider of {@link CalendarProfile}s that aren't loaded from
 * data-pack JSON. Implementations typically synthesize a profile at request
 * time from a third-party mod's public API (e.g., reading a calendar mod's
 * runtime month names so Townstead reflects whatever the user configured in
 * that mod, without Townstead embedding their creative content).
 *
 * Registered via {@link DynamicProfileSources}. Consulted by
 * {@link TownsteadCalendar#activeProfile} before the JSON registry, so a
 * dynamic source can shadow or replace a profile id that no JSON file
 * defines.
 *
 * Sources must be cheap on the "wrong id" path — {@link #tryBuild} is called
 * for every profile resolution and should fast-fail when {@code id} doesn't
 * match what the source supplies.
 */
public interface DynamicProfileSource {

    /**
     * Build the profile this source supplies for {@code id}, or
     * {@link Optional#empty} if {@code id} isn't this source's concern, or
     * if the backing data isn't currently available (e.g., the target mod
     * isn't loaded). Must not throw — return empty on any internal error.
     */
    Optional<CalendarProfile> tryBuild(ResourceLocation id);

    /**
     * Profile ids this source can supply *right now*. Used by the calendar
     * profile picker UI to populate its dropdown. Should return empty if the
     * backing data isn't available (e.g., the target mod isn't loaded), so
     * inert sources don't pollute the choice list.
     *
     * Default empty for sources that don't surface their ids in UI; override
     * to make the profile selectable from the dropdown.
     */
    default Set<ResourceLocation> knownIds() {
        return Set.of();
    }
}
