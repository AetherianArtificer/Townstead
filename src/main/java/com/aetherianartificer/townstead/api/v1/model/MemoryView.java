package com.aetherianartificer.townstead.api.v1.model;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** One entry in a villager's memory, with how strongly and how fondly it is held. */
public record MemoryView(
        String key,
        Optional<UUID> otherParty,
        long firstDay,
        long lastDay,
        int count,
        float strength,
        float valence,
        String source,
        boolean episodic,
        Map<String, String> params
) {
    public MemoryView {
        otherParty = otherParty == null ? Optional.empty() : otherParty;
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
