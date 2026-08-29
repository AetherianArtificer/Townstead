package com.aetherianartificer.townstead.work.station;

import org.jetbrains.annotations.Nullable;

/** What a worker does when its shift ends after inputs have already been committed. */
public enum ShiftEndPolicy {
    FINISH,
    LEAVE;

    public static @Nullable ShiftEndPolicy parse(String value) {
        return switch (value) {
            case "finish" -> FINISH;
            case "leave" -> LEAVE;
            default -> null;
        };
    }
}
