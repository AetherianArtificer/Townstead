package com.aetherianartificer.townstead.emote;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerEmoteProviders {
    private static final List<PlayerEmoteEventSource> SOURCES = List.of(
            new EmotecraftReflectionBackend()
    );
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private PlayerEmoteProviders() {}

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        for (PlayerEmoteEventSource source : SOURCES) {
            source.initialize();
        }
    }
}
