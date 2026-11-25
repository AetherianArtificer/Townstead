package com.aetherianartificer.townstead.pheno.action.block;

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

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory
            .getLogger("townstead/BlockActionTypes");
    private static final Map<String, BlockActionType> TYPES = new LinkedHashMap<>();

    private BlockActionTypes() {}

    public static void register(BlockActionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        BlockActionType existing = TYPES.put(key, type);
        if (existing != null) {
            LOGGER.warn("Block action type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    /** The type for a key: this copy's own, else the other copy of Pheno's, when that is installed. */
    public static Optional<BlockActionType> get(String key) {
        Optional<BlockActionType> own = own(key);
        return own.isPresent() ? own : com.aetherianartificer.townstead.compat.pheno.ForeignTypes.blockAction(key);
    }

    /** This copy's own type for a key, never the other copy's. The bridge asks only this, so a lookup never loops. */
    public static Optional<BlockActionType> own(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, BlockActionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
