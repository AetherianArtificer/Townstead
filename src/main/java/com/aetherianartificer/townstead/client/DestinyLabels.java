package com.aetherianartificer.townstead.client;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

/** The server's names for culture village structures, by structure id, for MCA's Destiny screen. */
public final class DestinyLabels {
    private static volatile Map<String, String> labels = Map.of();

    private DestinyLabels() {}

    public static void set(Map<String, String> next) {
        labels = Map.copyOf(next);
    }

    public static @Nullable String get(String structure) {
        return labels.get(structure);
    }
}
