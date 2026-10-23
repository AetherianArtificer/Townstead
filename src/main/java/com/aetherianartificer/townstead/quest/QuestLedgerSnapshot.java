package com.aetherianartificer.townstead.quest;

import java.util.List;

/** One atomic refresh of all quest providers. */
public record QuestLedgerSnapshot(List<QuestEntry> quests, List<QuestProviderInfo> providers) {
    public QuestLedgerSnapshot {
        quests = List.copyOf(quests);
        providers = List.copyOf(providers);
    }

    public int availableProviderCount() {
        return (int) providers.stream().filter(QuestProviderInfo::available).count();
    }
}
