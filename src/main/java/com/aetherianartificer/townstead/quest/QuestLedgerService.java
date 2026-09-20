package com.aetherianartificer.townstead.quest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Aggregates isolated optional providers and owns ledger-local pin state. */
public final class QuestLedgerService {
    private static final Comparator<QuestEntry> ORDER = Comparator
            .comparing(QuestEntry::pinned).reversed()
            .thenComparing(QuestEntry::group, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(QuestEntry::title, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(QuestEntry::key);

    private final List<QuestProvider> providers;
    private final Map<String, QuestProvider> byId;
    private final Map<String, Object> changeTokens = new LinkedHashMap<>();
    private final Set<String> localPins = new HashSet<>();
    private QuestLedgerSnapshot snapshot = new QuestLedgerSnapshot(List.of(), List.of());

    public QuestLedgerService(List<QuestProvider> providers) {
        LinkedHashMap<String, QuestProvider> unique = new LinkedHashMap<>();
        for (QuestProvider provider : providers) unique.putIfAbsent(provider.id(), provider);
        this.providers = List.copyOf(unique.values());
        this.byId = Map.copyOf(unique);
    }

    public QuestLedgerSnapshot refresh() {
        List<QuestEntry> quests = new ArrayList<>();
        List<QuestProviderInfo> info = new ArrayList<>();
        for (QuestProvider provider : providers) {
            changeTokens.put(provider.id(), changeToken(provider));
            boolean available;
            try {
                available = provider.isAvailable();
            } catch (Throwable failure) {
                info.add(new QuestProviderInfo(provider.id(), provider.displayName(), false, 0,
                        provider.capabilityNote(), concise(failure)));
                continue;
            }
            if (!available) {
                info.add(new QuestProviderInfo(provider.id(), provider.displayName(), false, 0,
                        provider.capabilityNote(), ""));
                continue;
            }
            try {
                List<QuestEntry> loaded = provider.loadQuests();
                for (QuestEntry entry : loaded) {
                    if (!provider.id().equals(entry.providerId())) continue;
                    quests.add(entry.withPinned(entry.pinned() || localPins.contains(entry.key())));
                }
                info.add(new QuestProviderInfo(provider.id(), provider.displayName(), true, loaded.size(),
                        provider.capabilityNote(), ""));
            } catch (Throwable failure) {
                info.add(new QuestProviderInfo(provider.id(), provider.displayName(), true, 0,
                        provider.capabilityNote(), concise(failure)));
            }
        }
        quests.sort(ORDER);
        snapshot = new QuestLedgerSnapshot(quests, info);
        return snapshot;
    }

    /** True when a provider has replaced an asynchronously synchronized client cache. */
    public boolean hasExternalChanges() {
        for (QuestProvider provider : providers) {
            if (changeToken(provider) != changeTokens.get(provider.id())) return true;
        }
        return false;
    }

    public QuestLedgerSnapshot snapshot() {
        return snapshot;
    }

    public List<QuestEntry> filtered(QuestFilter filter) {
        return snapshot.quests().stream().filter(filter::matches).sorted(ORDER).toList();
    }

    public QuestEntry toggleLocalPin(QuestEntry quest) {
        boolean pinned;
        if (localPins.remove(quest.key())) {
            pinned = false;
        } else {
            localPins.add(quest.key());
            pinned = true;
        }
        replace(quest.withPinned(pinned));
        return quest.withPinned(pinned);
    }

    /** Applies a successful client action immediately while the owning mod synchronizes its cache. */
    public void updateEntry(QuestEntry quest) {
        replace(quest);
    }

    public QuestActionResult perform(QuestAction action, QuestEntry quest) {
        QuestProvider provider = byId.get(quest.providerId());
        if (provider == null) return QuestActionResult.unavailable("Quest provider is no longer registered.");
        try {
            return provider.perform(action, quest);
        } catch (Throwable failure) {
            return QuestActionResult.unavailable(concise(failure));
        }
    }

    private void replace(QuestEntry replacement) {
        List<QuestEntry> changed = new ArrayList<>(snapshot.quests().size());
        for (QuestEntry entry : snapshot.quests()) {
            changed.add(entry.key().equals(replacement.key()) ? replacement : entry);
        }
        changed.sort(ORDER);
        snapshot = new QuestLedgerSnapshot(changed, snapshot.providers());
    }

    private static String concise(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }

    private static Object changeToken(QuestProvider provider) {
        try {
            return provider.changeToken();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
