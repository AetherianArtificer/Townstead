package com.aetherianartificer.townstead.pheno.action.item;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of {@link ItemActionType}s keyed by wire string. Clone of {@code ActionTypes};
 * {@link ItemActions#parse} looks up the type per item-action JSON.
 */
public final class ItemActionTypes {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(
            "townstead/ItemActionTypes");
    private static final Map<String, ItemActionType> TYPES = new LinkedHashMap<>();

    private ItemActionTypes() {}

    public static void register(ItemActionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ItemActionType existing = TYPES.put(key, type);
        if (existing != null) {
            LOGGER.warn("Item action type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    /** The type for a key: this copy's own, else the other copy of Pheno's, when that is installed. */
    public static Optional<ItemActionType> get(String key) {
        Optional<ItemActionType> own = own(key);
        return own.isPresent() ? own : com.aetherianartificer.townstead.compat.pheno.ForeignTypes.itemAction(key);
    }

    /** This copy's own type for a key, never the other copy's. The bridge asks only this, so a lookup never loops. */
    public static Optional<ItemActionType> own(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ItemActionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
