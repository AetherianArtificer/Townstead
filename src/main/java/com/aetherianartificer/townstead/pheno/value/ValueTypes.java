package com.aetherianartificer.townstead.pheno.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Registry of {@link ValueType}s keyed by wire string. Clone of {@code ActionTypes}. */
public final class ValueTypes {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
            "townstead/ValueTypes");
    private static final Map<String, ValueType> TYPES = new LinkedHashMap<>();

    private ValueTypes() {}

    public static void register(ValueType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ValueType existing = TYPES.put(key, type);
        if (existing != null) {
            LOGGER.warn("Value type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    /** The type for a key: this copy's own, else the other copy of Pheno's, when that is installed. */
    public static Optional<ValueType> get(String key) {
        Optional<ValueType> own = own(key);
        return own.isPresent() ? own : com.aetherianartificer.townstead.compat.pheno.ForeignTypes.value(key);
    }

    /** This copy's own type for a key, never the other copy's. The bridge asks only this, so a lookup never loops. */
    public static Optional<ValueType> own(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ValueType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
