package com.aetherianartificer.townstead.origin.gene;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link GeneType}s keyed by their wire string (a gene JSON's
 * {@code "type"} field). Populated once at startup; {@code GeneJsonLoader} looks
 * up the type per gene file to delegate parsing. Clone of {@code TriggerTypes}.
 */
public final class GeneTypes {
    private static final Map<String, GeneType> TYPES = new LinkedHashMap<>();

    private GeneTypes() {}

    public static void register(GeneType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        GeneType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Gene type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<GeneType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, GeneType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
