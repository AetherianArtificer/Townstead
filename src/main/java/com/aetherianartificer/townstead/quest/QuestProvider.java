package com.aetherianartificer.townstead.quest;

import java.util.List;

/** Optional quest-system adapter. Implementations must not throw when their provider is absent. */
public interface QuestProvider {
    String id();

    String displayName();

    boolean isAvailable();

    List<QuestEntry> loadQuests() throws Exception;

    /**
     * Identity token for asynchronously replaced provider data. Providers whose client cache is
     * pushed after this ledger opens can expose the cache object here so the screen refreshes when
     * its identity changes.
     */
    default Object changeToken() {
        return null;
    }

    default String capabilityNote() {
        return "";
    }

    default QuestActionResult perform(QuestAction action, QuestEntry quest) {
        return QuestActionResult.unavailable("This action belongs to " + displayName() + ".");
    }
}
