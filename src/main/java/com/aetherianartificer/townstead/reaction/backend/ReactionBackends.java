package com.aetherianartificer.townstead.reaction.backend;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link ReactionBackend} implementations keyed by lowercased
 * prefix string. Populated once at mod startup. The dispatcher looks up
 * the backend per binding via {@link #get(String)} using the prefix
 * parsed out of the binding's {@code ref}.
 */
public final class ReactionBackends {
    private static final Map<String, ReactionBackend> BACKENDS = new LinkedHashMap<>();

    private ReactionBackends() {}

    public static void register(ReactionBackend backend) {
        if (backend == null || backend.key() == null) return;
        String key = backend.key().toLowerCase(Locale.ROOT);
        ReactionBackend existing = BACKENDS.put(key, backend);
        if (existing != null) {
            Townstead.LOGGER.warn("Reaction backend '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), backend.getClass().getName());
        }
    }

    public static Optional<ReactionBackend> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(BACKENDS.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ReactionBackend> all() {
        return Collections.unmodifiableMap(BACKENDS);
    }
}
