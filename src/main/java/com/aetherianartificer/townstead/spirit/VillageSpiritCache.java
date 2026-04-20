package com.aetherianartificer.townstead.spirit;

import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory per-village cache of the last computed {@link SpiritTotals} and
 * {@link SpiritReadout}. Seeded on server start (silent) so the player's first
 * action doesn't emit tier-change events for every pre-existing village. Lives
 * for the server's JVM session — on restart, everything re-seeds from a fresh
 * pass over the buildings.
 *
 * No SavedData: the inputs (village buildings + {@link BuildingSpiritIndex})
 * are both already persistent, so a recomputed cache converges to the same
 * state immediately.
 */
public final class VillageSpiritCache {
    /** Key: "<dim>|<villageId>". */
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();

    public record Entry(SpiritTotals totals, SpiritReadout readout) {}

    private VillageSpiritCache() {}

    public static @Nullable Entry get(ServerLevel level, int villageId) {
        return CACHE.get(keyOf(level, villageId));
    }

    public static void put(ServerLevel level, int villageId, Entry entry) {
        CACHE.put(keyOf(level, villageId), entry);
    }

    public static void remove(ServerLevel level, int villageId) {
        CACHE.remove(keyOf(level, villageId));
    }

    public static void clear() {
        CACHE.clear();
    }

    private static String keyOf(ServerLevel level, int villageId) {
        return level.dimension().location().toString() + "|" + villageId;
    }
}
