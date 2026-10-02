package com.aetherianartificer.townstead.quest;

import java.util.Objects;

/** Status shown by the ledger for one configured adapter. */
public record QuestProviderInfo(
        String id, String displayName, boolean available, int questCount, String note, String error
) {
    public QuestProviderInfo {
        id = Objects.requireNonNullElse(id, "unknown");
        displayName = Objects.requireNonNullElse(displayName, id);
        questCount = Math.max(0, questCount);
        note = Objects.requireNonNullElse(note, "");
        error = Objects.requireNonNullElse(error, "");
    }
}
