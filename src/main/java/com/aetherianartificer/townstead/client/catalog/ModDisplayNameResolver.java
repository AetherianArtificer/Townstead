package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves a Forge/NeoForge mod's display name (as shown in the mod menu) from
 * its mod-id. Results are cached so the catalog detail panel doesn't poke
 * {@code ModList} on every selection. Falls back to a capitalized mod-id if
 * the mod isn't loaded.
 *
 * <p>Pre-warmed at client login for every {@code compat/<mod>/*} building type
 * so the first detail-panel open doesn't pay the {@code ModList} lookup.
 */
public final class ModDisplayNameResolver {
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<>();
    private static final AtomicBoolean ASYNC_WARM_RUNNING = new AtomicBoolean(false);
    /**
     * Set true once a full BuildingTypes warm has finished so the catalog-open
     * fallback short-circuits without iterating BuildingTypes again.
     */
    private static final AtomicBoolean WARMED = new AtomicBoolean(false);

    private ModDisplayNameResolver() {}

    /** Returns a display name for {@code modId}, falling back to its capitalized id. */
    public static String displayName(String modId) {
        if (modId == null || modId.isEmpty()) return modId == null ? "" : modId;
        String cached = CACHE.get(modId);
        if (cached != null) return cached;
        String resolved = resolve(modId);
        CACHE.put(modId, resolved);
        return resolved;
    }

    public static void prewarmAsync(Collection<String> modIds) {
        if (modIds == null || modIds.isEmpty()) return;
        if (!ASYNC_WARM_RUNNING.compareAndSet(false, true)) return;
        List<String> snapshot = new ArrayList<>(modIds);
        ForkJoinPool.commonPool().execute(() -> {
            try {
                for (String modId : snapshot) {
                    if (!CACHE.containsKey(modId)) CACHE.put(modId, resolve(modId));
                }
                WARMED.set(true);
            } catch (Throwable t) {
                Townstead.LOGGER.warn("ModDisplayNameResolver prewarm failed", t);
            } finally {
                ASYNC_WARM_RUNNING.set(false);
            }
        });
    }

    /**
     * Convenience entry point: gathers every {@code compat/<mod>/*} building
     * type's mod id on the calling (main) thread and dispatches the async warm.
     * Idempotent — short-circuits once warmed or while a warm is in flight.
     */
    public static void prewarmAllFromBuildingTypes() {
        if (WARMED.get() || ASYNC_WARM_RUNNING.get()) return;
        LinkedHashSet<String> modIds = new LinkedHashSet<>();
        try {
            for (BuildingType bt : BuildingTypes.getInstance()) {
                String name = bt.name();
                if (!name.startsWith("compat/")) continue;
                String[] parts = name.split("/");
                if (parts.length < 2) continue;
                modIds.add(parts[1]);
            }
        } catch (Throwable t) {
            Townstead.LOGGER.warn("ModDisplayNameResolver: failed to collect mod ids", t);
            return;
        }
        prewarmAsync(modIds);
    }

    private static String resolve(String modId) {
        String resolved = lookup(modId);
        if (resolved == null || resolved.isEmpty()) {
            return modId.substring(0, 1).toUpperCase(Locale.ROOT) + modId.substring(1);
        }
        return resolved;
    }

    private static String lookup(String modId) {
        try {
            //? if neoforge {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById(modId)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(null);
            //?} else {
            /*return net.minecraftforge.fml.ModList.get()
                    .getModContainerById(modId)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(null);
            *///?}
        } catch (Throwable t) {
            return null;
        }
    }
}
