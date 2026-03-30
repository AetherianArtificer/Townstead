package com.aetherianartificer.townstead.emote;

public interface PlayerEmoteEventSource {
    String providerId();

    boolean isActive();

    void initialize();
}
