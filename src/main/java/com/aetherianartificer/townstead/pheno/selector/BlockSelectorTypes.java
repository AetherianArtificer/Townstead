package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Registry of {@link BlockSelectorType}s keyed by wire string. Clone of {@code SelectorTypes}. */
public final class BlockSelectorTypes {

    private static final Map<String, BlockSelectorType> TYPES = new LinkedHashMap<>();

    private BlockSelectorTypes() {}

    public static void register(BlockSelectorType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        BlockSelectorType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Block selector type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<BlockSelectorType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, BlockSelectorType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
