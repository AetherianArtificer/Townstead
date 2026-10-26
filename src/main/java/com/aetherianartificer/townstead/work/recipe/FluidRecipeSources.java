package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named readers that turn a mod's fluid recipes into workable item-in, item-out ones, referenced
 * from a workstation def by {@code "fluid_source"} — the same seam as {@code "adapter"}.
 *
 * <p>A fluid recipe never comes through the vanilla recipe API, so each mod needs its own reading,
 * and each reading has to stay in that mod's compat package. Naming the reader in data is what
 * keeps the engine from having to know which mods exist: a def says which vocabulary its station
 * speaks, and the compat class registers itself when its mod is present.</p>
 */
public final class FluidRecipeSources {

    public interface Source {
        /** Every recipe this mod's fluid stations can work, already joined into item form. */
        List<DiscoveredRecipe> discover(ServerLevel level, StationType stationType, int tier);
    }

    private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();

    private FluidRecipeSources() {}

    public static void register(String name, Source source) {
        if (name == null || source == null) return;
        SOURCES.put(name.toLowerCase(Locale.ROOT), source);
    }

    /** The reader for a def's declared source, or null when none is named or none is loaded. */
    @Nullable
    public static Source byName(@Nullable String name) {
        if (name == null || name.isBlank()) return null;
        return SOURCES.get(name.toLowerCase(Locale.ROOT));
    }
}
