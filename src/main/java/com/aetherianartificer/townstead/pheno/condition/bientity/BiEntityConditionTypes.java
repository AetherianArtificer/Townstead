package com.aetherianartificer.townstead.pheno.condition.bientity;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link BiEntityConditionType}s keyed by wire string. Clone of
 * {@code ConditionTypes}; {@link BiEntityConditions#parse} looks up the type per JSON.
 */
public final class BiEntityConditionTypes {

    private static final Map<String, BiEntityConditionType> TYPES = new LinkedHashMap<>();

    private BiEntityConditionTypes() {}

    public static void register(BiEntityConditionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        BiEntityConditionType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Bi-entity condition type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    /** The type for a key: this copy's own, else the other copy of Pheno's, when that is installed. */
    public static Optional<BiEntityConditionType> get(String key) {
        Optional<BiEntityConditionType> own = own(key);
        return own.isPresent() ? own : com.aetherianartificer.townstead.compat.pheno.ForeignTypes.biEntityCondition(key);
    }

    /** This copy's own type for a key, never the other copy's. The bridge asks only this, so a lookup never loops. */
    public static Optional<BiEntityConditionType> own(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, BiEntityConditionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
