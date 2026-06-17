package com.aetherianartificer.townstead.pheno.condition;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link ConditionType}s keyed by wire string. Populated once at
 * startup; {@link Conditions#parse} looks up the type per condition JSON. Clone
 * of {@code GeneTypes}.
 */
public final class ConditionTypes {
    private static final Map<String, ConditionType> TYPES = new LinkedHashMap<>();

    private ConditionTypes() {}

    public static void register(ConditionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ConditionType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Condition type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<ConditionType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ConditionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
