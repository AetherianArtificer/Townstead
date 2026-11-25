package com.aetherianartificer.townstead.pheno.condition;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

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
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ConditionType> TYPES = new LinkedHashMap<>();

    private ConditionTypes() {}

    public static void register(ConditionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ConditionType existing = TYPES.put(key, type);
        if (existing != null) {
            LOGGER.warn("Condition type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    /** The type for a key: this copy's own, else the other copy of Pheno's, when that is installed. */
    public static Optional<ConditionType> get(String key) {
        Optional<ConditionType> own = own(key);
        return own.isPresent() ? own : com.aetherianartificer.townstead.compat.pheno.ForeignTypes.condition(key);
    }

    /** This copy's own type for a key, never the other copy's. The bridge asks only this, so a lookup never loops. */
    public static Optional<ConditionType> own(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ConditionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
