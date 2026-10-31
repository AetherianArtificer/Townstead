package com.aetherianartificer.townstead.chronicle.net;

import com.aetherianartificer.townstead.chronicle.model.VillageHistory;

import java.util.ArrayList;
import java.util.List;

/** Stable newest-first cursor paging for append-only public digest entries. */
public final class ChronicleDigestPager {
    private ChronicleDigestPager() {}

    public record Page(List<VillageHistory.Entry> entries, boolean hasMore, long nextCursor) {}

    public static Page page(List<VillageHistory.Entry> source, long beforeEventId, int requestedLimit) {
        int limit = Math.max(1, requestedLimit);
        List<VillageHistory.Entry> page = new ArrayList<>(limit);
        boolean more = false;
        for (int i = source.size() - 1; i >= 0; i--) {
            VillageHistory.Entry entry = source.get(i);
            if (beforeEventId > 0 && entry.eventId() >= beforeEventId) continue;
            if (page.size() == limit) {
                more = true;
                break;
            }
            page.add(entry);
        }
        long next = page.isEmpty() ? 0L : page.get(page.size() - 1).eventId();
        return new Page(List.copyOf(page), more, next);
    }
}
