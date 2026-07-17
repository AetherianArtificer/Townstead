package com.aetherianartificer.townstead.chronicle.emit;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Once-per-day gates for pair-scoped chronicle taps (friendship, argument,
 * marriage), so recurring interactions produce one story, not a stream.
 */
public final class ChronicleRateLimiter {

    private static final int CLEANUP_THRESHOLD = 4096;

    private static final Map<String, Long> STAMPS = new ConcurrentHashMap<>();

    private ChronicleRateLimiter() {}

    /** True once per (unordered pair, category) per calendar day; stamps on success. */
    public static boolean allowPair(MinecraftServer server, UUID a, UUID b, String category) {
        long today = TownsteadCalendar.worldDay(server);
        String key = pairKey(a, b, category);
        Long last = STAMPS.get(key);
        if (last != null && last == today) return false;
        if (STAMPS.size() > CLEANUP_THRESHOLD) {
            STAMPS.values().removeIf(day -> today - day > 1);
        }
        STAMPS.put(key, today);
        return true;
    }

    private static String pairKey(UUID a, UUID b, String category) {
        return a.compareTo(b) <= 0
                ? a + "|" + b + "|" + category
                : b + "|" + a + "|" + category;
    }

    public static int size() {
        return STAMPS.size();
    }

    public static void clearAll() {
        STAMPS.clear();
    }
}
