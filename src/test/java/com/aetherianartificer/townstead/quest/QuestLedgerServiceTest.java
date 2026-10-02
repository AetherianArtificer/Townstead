package com.aetherianartificer.townstead.quest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestLedgerServiceTest {
    @Test
    void aggregatesAvailableProvidersAndIsolatesFailures() {
        QuestProvider good = provider("good", true, List.of(quest("good", "oak", QuestState.ACTIVE)));
        QuestProvider missing = provider("missing", false, List.of());
        QuestProvider broken = new StubProvider("broken", true, List.of()) {
            @Override public List<QuestEntry> loadQuests() { throw new IllegalStateException("bad cache"); }
        };

        QuestLedgerSnapshot snapshot = new QuestLedgerService(List.of(good, missing, broken)).refresh();

        assertEquals(List.of("good:oak"), snapshot.quests().stream().map(QuestEntry::key).toList());
        assertEquals(2, snapshot.availableProviderCount());
        assertTrue(snapshot.providers().stream().filter(p -> p.id().equals("good")).findFirst().orElseThrow()
                .error().isBlank());
        assertFalse(snapshot.providers().stream().filter(p -> p.id().equals("missing")).findFirst().orElseThrow().available());
        assertTrue(snapshot.providers().stream().filter(p -> p.id().equals("broken")).findFirst().orElseThrow()
                .error().contains("bad cache"));
    }

    @Test
    void filtersByStateProviderAndTextAcrossNormalizedFields() {
        QuestEntry active = new QuestEntry("one", "MCA: Quests", "roof", "A Roof Before Rain",
                "Build Elowen a safe home", "Village matters", "minecraft:book", QuestState.ACTIVE,
                List.of(new QuestObjective("Gather oak planks", 32, 64, QuestObjective.Status.PENDING, "")),
                List.of(new QuestReward("Emeralds", "minecraft:emerald")), Set.of(), false, false, List.of(), "");
        QuestEntry complete = quest("two", "granary", QuestState.COMPLETE);
        QuestLedgerService service = new QuestLedgerService(List.of(
                provider("one", true, List.of(active)), provider("two", true, List.of(complete))));
        service.refresh();

        assertEquals(List.of(active), service.filtered(new QuestFilter(QuestState.ACTIVE, "one", "elowen")));
        assertEquals(List.of(active), service.filtered(new QuestFilter(QuestState.ACTIVE, "*", "oak")));
        assertEquals(List.of(active), service.filtered(new QuestFilter(QuestState.ACTIVE, "*", "emerald")));
        assertTrue(service.filtered(new QuestFilter(QuestState.AVAILABLE, "*", "")).isEmpty());
    }

    @Test
    void localPinIsImmediatePersistsAcrossRefreshAndSortsFirst() {
        QuestEntry alpha = quest("one", "alpha", QuestState.ACTIVE);
        QuestEntry beta = quest("one", "beta", QuestState.ACTIVE);
        QuestLedgerService service = new QuestLedgerService(List.of(provider("one", true, List.of(alpha, beta))));
        service.refresh();

        QuestEntry pinned = service.toggleLocalPin(beta);
        assertTrue(pinned.pinned());
        assertEquals("beta", service.snapshot().quests().get(0).id());

        service.refresh();
        assertTrue(service.snapshot().quests().stream().filter(q -> q.id().equals("beta")).findFirst().orElseThrow().pinned());
        assertFalse(service.toggleLocalPin(pinned).pinned());
    }

    @Test
    void transientProviderStateUpdatesTheCurrentSnapshot() {
        QuestEntry entry = quest("one", "alpha", QuestState.ACTIVE);
        QuestLedgerService service = new QuestLedgerService(List.of(provider("one", true, List.of(entry))));
        service.refresh();

        service.updateEntry(entry.withTracked(true).withPinned(true));

        QuestEntry updated = service.snapshot().quests().get(0);
        assertTrue(updated.tracked());
        assertTrue(updated.pinned());
    }

    @Test
    void detectsIdentityReplacementOfAnAsynchronousProviderCache() {
        AtomicReference<Object> token = new AtomicReference<>(new Object());
        QuestProvider provider = new StubProvider("async", true, List.of()) {
            @Override public Object changeToken() { return token.get(); }
        };
        QuestLedgerService service = new QuestLedgerService(List.of(provider));

        service.refresh();
        assertFalse(service.hasExternalChanges());

        token.set(new Object());
        assertTrue(service.hasExternalChanges());

        service.refresh();
        assertFalse(service.hasExternalChanges());
    }

    @Test
    void routesActionsOnlyToOwningProviderAndNeverLeaksAnException() {
        AtomicReference<QuestAction> seen = new AtomicReference<>();
        QuestProvider owner = new StubProvider("owner", true, List.of()) {
            @Override public QuestActionResult perform(QuestAction action, QuestEntry quest) {
                seen.set(action);
                if (action == QuestAction.PIN) throw new IllegalArgumentException("packet rejected");
                return QuestActionResult.ok("opened");
            }
        };
        QuestLedgerService service = new QuestLedgerService(List.of(owner));
        QuestEntry entry = quest("owner", "id", QuestState.ACTIVE);

        assertTrue(service.perform(QuestAction.OPEN_SOURCE, entry).success());
        assertEquals(QuestAction.OPEN_SOURCE, seen.get());
        assertFalse(service.perform(QuestAction.PIN, entry).success());
        assertTrue(service.perform(QuestAction.PIN, entry).message().contains("packet rejected"));
        assertFalse(service.perform(QuestAction.TRACK, quest("gone", "id", QuestState.ACTIVE)).success());
    }

    @Test
    void normalizesProviderDataAndObjectiveProgress() {
        QuestObjective objective = new QuestObjective(null, -4, 2, null, null);
        QuestEntry entry = new QuestEntry(null, null, null, null, null, null, null, null,
                List.of(new QuestObjective("Done", 3, 2, QuestObjective.Status.PENDING, "")),
                null, null, false, false, null, null);

        assertEquals(0, objective.current());
        assertEquals("unknown:unknown", entry.key());
        assertEquals(1, entry.completedObjectives());
        assertTrue(entry.withTracked(true).tracked());
    }

    private static QuestEntry quest(String provider, String id, QuestState state) {
        return new QuestEntry(provider, provider, id, id, "", "General", "minecraft:paper", state,
                List.of(), List.of(), Set.of(), false, false, List.of(), "");
    }

    private static QuestProvider provider(String id, boolean available, List<QuestEntry> quests) {
        return new StubProvider(id, available, quests);
    }

    private static class StubProvider implements QuestProvider {
        private final String id;
        private final boolean available;
        private final List<QuestEntry> quests;

        private StubProvider(String id, boolean available, List<QuestEntry> quests) {
            this.id = id;
            this.available = available;
            this.quests = quests;
        }

        @Override public String id() { return id; }
        @Override public String displayName() { return id; }
        @Override public boolean isAvailable() { return available; }
        @Override public List<QuestEntry> loadQuests() { return quests; }
    }
}
