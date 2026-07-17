package com.aetherianartificer.townstead.client.chronicle;

import com.aetherianartificer.townstead.chronicle.net.ChroniclePageS2CPayload;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Client-side page cache for the chronicle viewer, keyed by request id.
 */
public final class ChronicleClientStore {

    private static final AtomicInteger NEXT_REQUEST = new AtomicInteger(1);
    private static final Map<Integer, ChroniclePageS2CPayload> PAGES = new ConcurrentHashMap<>();

    private ChronicleClientStore() {}

    public static int nextRequestId() {
        return NEXT_REQUEST.getAndIncrement();
    }

    public static void put(ChroniclePageS2CPayload page) {
        PAGES.put(page.requestId(), page);
    }

    public static @Nullable ChroniclePageS2CPayload take(int requestId) {
        return PAGES.remove(requestId);
    }

    public static void clear() {
        PAGES.clear();
    }
}
