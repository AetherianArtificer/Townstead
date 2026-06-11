package com.aetherianartificer.townstead.pheno.action.item;

import com.aetherianartificer.townstead.Townstead;

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

    private static final Map<String, ItemActionType> TYPES = new LinkedHashMap<>();

    private ItemActionTypes() {}

    public static void register(ItemActionType type) {
        if (type == null || type.key() == null) return;
        String key = type.key().toLowerCase(Locale.ROOT);
        ItemActionType existing = TYPES.put(key, type);
        if (existing != null) {
            Townstead.LOGGER.warn("Item action type '{}' overwritten (was {}, now {})", key,
                    existing.getClass().getName(), type.getClass().getName());
        }
    }

    public static Optional<ItemActionType> get(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(TYPES.get(key.toLowerCase(Locale.ROOT)));
    }

    public static Map<String, ItemActionType> all() {
        return Collections.unmodifiableMap(TYPES);
    }
}
