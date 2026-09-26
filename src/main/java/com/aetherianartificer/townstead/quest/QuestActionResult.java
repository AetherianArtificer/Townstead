package com.aetherianartificer.townstead.quest;

import java.util.Objects;

/** Non-throwing result returned by provider actions. */
public record QuestActionResult(boolean success, String message) {
    public QuestActionResult {
        message = Objects.requireNonNullElse(message, "");
    }

    public static QuestActionResult ok(String message) {
        return new QuestActionResult(true, message);
    }

    public static QuestActionResult unavailable(String message) {
        return new QuestActionResult(false, message);
    }
}
