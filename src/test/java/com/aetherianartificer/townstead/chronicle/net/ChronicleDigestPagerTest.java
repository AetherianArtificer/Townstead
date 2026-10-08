package com.aetherianartificer.townstead.chronicle.net;

import com.aetherianartificer.townstead.chronicle.model.VillageHistory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChronicleDigestPagerTest {
    @Test
    void pagesNewestFirstWithoutDuplicates() {
        List<VillageHistory.Entry> source = entries(1, 7);
        ChronicleDigestPager.Page first = ChronicleDigestPager.page(source, 0L, 3);
        assertEquals(List.of(7L, 6L, 5L), ids(first));
        assertTrue(first.hasMore());
        assertEquals(5L, first.nextCursor());

        ChronicleDigestPager.Page second = ChronicleDigestPager.page(source, first.nextCursor(), 3);
        assertEquals(List.of(4L, 3L, 2L), ids(second));
        assertTrue(second.hasMore());

        ChronicleDigestPager.Page last = ChronicleDigestPager.page(source, second.nextCursor(), 3);
        assertEquals(List.of(1L), ids(last));
        assertFalse(last.hasMore());
    }

    @Test
    void newlyAppendedEntriesDoNotShiftAnExistingCursor() {
        List<VillageHistory.Entry> source = entries(1, 5);
        ChronicleDigestPager.Page first = ChronicleDigestPager.page(source, 0L, 2);
        source.add(entry(6));
        assertEquals(List.of(3L, 2L), ids(ChronicleDigestPager.page(source, first.nextCursor(), 2)));
    }

    private static List<VillageHistory.Entry> entries(int first, int last) {
        List<VillageHistory.Entry> entries = new ArrayList<>();
        for (int id = first; id <= last; id++) entries.add(entry(id));
        return entries;
    }

    private static VillageHistory.Entry entry(long id) {
        return new VillageHistory.Entry(id, id, "townstead:test", "Event " + id, "", Map.of());
    }

    private static List<Long> ids(ChronicleDigestPager.Page page) {
        return page.entries().stream().map(VillageHistory.Entry::eventId).toList();
    }
}
