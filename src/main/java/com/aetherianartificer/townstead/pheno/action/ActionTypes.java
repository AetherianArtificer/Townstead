package com.aetherianartificer.townstead.pheno.action;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link ActionType}s keyed by wire string. Populated once at startup;
 * {@link Actions#parse} looks up the type per action JSON. Clone of
 * {@code ConditionTypes}.
 */
public final class ActionTypes {
    private static final Map<String, ActionType> TYPES = new LinkedHashMap<>();

    private ActionTypes() {}

    public static void register(ActionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ActionType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Action type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<ActionType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ActionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
