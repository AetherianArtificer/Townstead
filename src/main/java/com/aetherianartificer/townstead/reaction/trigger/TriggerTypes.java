package com.aetherianartificer.townstead.reaction.trigger;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of trigger types keyed by their wire string (the {@code type}
 * field in trigger JSON). Populated once at startup; the data loader
 * looks up the type per parsed trigger to delegate parsing and indexing.
 */
public final class TriggerTypes {
    private static final Map<String, TriggerType> TYPES = new LinkedHashMap<>();

    private TriggerTypes() {}

    public static void register(TriggerType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        TriggerType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Trigger type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<TriggerType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, TriggerType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
