package com.aetherianartificer.townstead.origin.trait.effect;

import com.aetherianartificer.townstead.Townstead;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link TraitEffectType}s keyed by their wire key (a trait effect
 * entry's property). Populated once at startup; {@code TraitJsonLoader} looks the
 * key up per effect to delegate parsing. Clone of {@code GeneTypes}.
 */
public final class TraitEffectTypes {
    private static final Map<String, TraitEffectType> TYPES = new LinkedHashMap<>();

    private TraitEffectTypes() {}

    public static void register(TraitEffectType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        TraitEffectType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Trait effect type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<TraitEffectType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }
}
