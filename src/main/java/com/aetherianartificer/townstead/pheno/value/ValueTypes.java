package com.aetherianartificer.townstead.pheno.value;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Registry of {@link ValueType}s keyed by wire string. Clone of {@code ActionTypes}. */
public final class ValueTypes {

    private static final Map<String, ValueType> TYPES = new LinkedHashMap<>();

    private ValueTypes() {}

    public static void register(ValueType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ValueType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Value type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<ValueType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ValueType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
