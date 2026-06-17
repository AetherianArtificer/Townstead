package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Registry of {@link SelectorType}s keyed by wire string. Clone of {@code ActionTypes}. */
public final class SelectorTypes {

    private static final Map<String, SelectorType> TYPES = new LinkedHashMap<>();

    private SelectorTypes() {}

    public static void register(SelectorType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        SelectorType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Selector type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<SelectorType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, SelectorType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
