package com.aetherianartificer.townstead.pheno.action.block;

import com.aetherianartificer.townstead.Townstead;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link BlockActionType}s keyed by wire string. Clone of
 * {@code ActionTypes}; {@link BlockActions#parse} looks up the type per block-action JSON.
 */
public final class BlockActionTypes {

    private static final Map<String, BlockActionType> TYPES = new LinkedHashMap<>();

    private BlockActionTypes() {}

    public static void register(BlockActionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        BlockActionType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Block action type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<BlockActionType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, BlockActionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
