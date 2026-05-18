package com.aetherianartificer.townstead.calendar;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central registry of {@link DynamicProfileSource}s. Order of registration
 * = order of consultation. First non-empty result wins.
 *
 * Bootstrap registrations happen during mod init (see
 * {@code Townstead.commonSetup}). The list is copy-on-write so iteration
 * stays safe even if a future feature registers sources at runtime.
 */
public final class DynamicProfileSources {

    private static final CopyOnWriteArrayList<DynamicProfileSource> SOURCES = new CopyOnWriteArrayList<>();

    private DynamicProfileSources() {}

    public static void register(DynamicProfileSource source) {
        if (source != null) SOURCES.addIfAbsent(source);
    }

    /** First registered source that returns a non-empty {@link CalendarProfile}. */
    public static Optional<CalendarProfile> tryBuild(ResourceLocation id) {
        if (id == null) return Optional.empty();
        for (DynamicProfileSource source : SOURCES) {
            try {
                Optional<CalendarProfile> result = source.tryBuild(id);
                if (result != null && result.isPresent()) return result;
            } catch (Throwable ignored) {
                // Contract says sources must not throw; defensive guard
                // against a misbehaving impl.
            }
        }
        return Optional.empty();
    }

    /**
     * Union of every registered source's currently-available ids. Used by
     * the profile picker UI to enumerate dynamic options alongside JSON
     * registry entries. Order is registration order (insertion preserved by
     * {@link LinkedHashSet}); within each source, the source's own ordering.
     */
    public static Set<ResourceLocation> listKnownIds() {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (DynamicProfileSource source : SOURCES) {
            try {
                Set<ResourceLocation> ids = source.knownIds();
                if (ids != null) out.addAll(ids);
            } catch (Throwable ignored) {
                // Same defensive contract as tryBuild.
            }
        }
        return out;
    }
}
