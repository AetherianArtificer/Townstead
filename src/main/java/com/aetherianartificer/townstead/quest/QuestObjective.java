package com.aetherianartificer.townstead.quest;

import java.util.Objects;

/** A normalized objective line. A total of zero means the provider exposes no numeric goal. */
public record QuestObjective(String label, long current, long total, Status status, String iconItemId) {
    public enum Status { PENDING, DONE, UNAVAILABLE, FAILED }

    public QuestObjective {
        label = Objects.requireNonNullElse(label, "");
        current = Math.max(0L, current);
        total = Math.max(0L, total);
        status = Objects.requireNonNullElse(status, Status.PENDING);
        iconItemId = Objects.requireNonNullElse(iconItemId, "");
    }

    public boolean done() {
        return status == Status.DONE || total > 0L && current >= total;
    }
}
