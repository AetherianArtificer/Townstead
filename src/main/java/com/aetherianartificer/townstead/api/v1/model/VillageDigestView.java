package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;
import java.util.Map;

/** A village's public record: headlines newest first, and how many events of each category it has seen. */
public record VillageDigestView(
        VillageId village,
        Page<Entry> entries,
        Map<String, Integer> categoryCounts
) {
    public VillageDigestView {
        categoryCounts = categoryCounts == null ? Map.of() : Map.copyOf(categoryCounts);
    }

    /** One headline. {@code headline} is the resolved text; {@code headlineLangKey} plus {@code params} render it in another language. */
    public record Entry(
            long worldDay,
            long eventId,
            String templateId,
            String headline,
            String headlineLangKey,
            Map<String, String> params
    ) {
        public Entry {
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }
}
