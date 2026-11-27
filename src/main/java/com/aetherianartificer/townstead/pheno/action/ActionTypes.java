package com.aetherianartificer.townstead.pheno.action;

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
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
            "townstead/ActionTypes");
    private static final Map<String, ActionType> TYPES = new LinkedHashMap<>();

    private ActionTypes() {}

    public static void register(ActionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ActionType existing = TYPES.put(key, type);
        if (existing != null) {
            LOGGER.warn("Action type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    /** The type for a key: this copy's own, else the other copy of Pheno's, when that is installed. */
    public static Optional<ActionType> get(String key) {
        Optional<ActionType> own = own(key);
        return own.isPresent() ? own : com.aetherianartificer.townstead.compat.pheno.ForeignTypes.action(key);
    }

    /** This copy's own type for a key, never the other copy's. The bridge asks only this, so a lookup never loops. */
    public static Optional<ActionType> own(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ActionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
