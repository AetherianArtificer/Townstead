package com.aetherianartificer.townstead.quest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalTrackerLedgerTest {

    /** Keeps at most two keys, dropping the oldest, like the client tracker does with three. */
    private static final class TwoSlotTracker implements LocalTracker {
        final LinkedHashSet<String> keys = new LinkedHashSet<>();
        @Override public boolean isTracked(String key) { return keys.contains(key); }
        @Override public boolean toggle(String key) {
            if (keys.remove(key)) return false;
            keys.add(key);
            if (keys.size() > 2) keys.remove(keys.iterator().next());
            return true;
        }
    }

    private static QuestProvider provider(String id, boolean local, String... quests) {
        return new QuestProvider() {
            @Override public String id() { return id; }
            @Override public String displayName() { return id; }
            @Override public boolean isAvailable() { return true; }
            @Override public boolean usesLocalTracker() { return local; }
            @Override public List<QuestEntry> loadQuests() {
                List<QuestEntry> out = new ArrayList<>();
                for (String quest : quests) {
                    out.add(new QuestEntry(id, id, quest, quest, "", id, "", QuestState.ACTIVE, List.of(), List.of(),
                            Set.of(), false, false, List.of(), ""));
                }
                return out;
            }
        };
    }

    @Test
    void onlyLocalProvidersGainTrack() {
        QuestLedgerService ledger = new QuestLedgerService(
                List.of(provider("stories", true, "a"), provider("ftb", false, "b")), new TwoSlotTracker());
        ledger.refresh();
        assertTrue(entry(ledger, "stories:a").capabilities().contains(QuestCapability.TRACK));
        assertFalse(entry(ledger, "ftb:b").capabilities().contains(QuestCapability.TRACK));
    }

    @Test
    void trackingPastTheLimitClearsTheDroppedRow() {
        TwoSlotTracker tracker = new TwoSlotTracker();
        QuestLedgerService ledger = new QuestLedgerService(List.of(provider("stories", true, "a", "b", "c")), tracker);
        ledger.refresh();
        for (String id : List.of("a", "b", "c")) {
            assertTrue(ledger.perform(QuestAction.TRACK, entry(ledger, "stories:" + id)).success());
        }
        assertFalse(entry(ledger, "stories:a").tracked());
        assertTrue(entry(ledger, "stories:b").tracked());
        assertTrue(entry(ledger, "stories:c").tracked());
        assertEquals(List.of("stories:b", "stories:c"), List.copyOf(tracker.keys));
    }

    private static QuestEntry entry(QuestLedgerService ledger, String key) {
        return ledger.snapshot().quests().stream().filter(q -> q.key().equals(key)).findFirst().orElseThrow();
    }
}
