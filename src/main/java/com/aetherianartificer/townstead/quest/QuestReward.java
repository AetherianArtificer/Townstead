package com.aetherianartificer.townstead.quest;

import java.util.Objects;

/** A normalized reward preview. */
public record QuestReward(String label, String iconItemId) {
    public QuestReward {
        label = Objects.requireNonNullElse(label, "");
        iconItemId = Objects.requireNonNullElse(iconItemId, "");
    }
}
