package com.aetherianartificer.townstead.client;

import net.minecraft.client.resources.language.I18n;

import java.util.Optional;

/**
 * Player-facing names for resource namespaces supplied by globally mounted packs.
 *
 * <p>A namespace is a stable technical identity, not display copy. A client language pack may
 * provide {@code townstead.pack.<namespace>} to name the pack, collection, or author responsible
 * for resources in that namespace. Integrations can then use the same authored name without
 * making packs declare integration-specific keys.</p>
 */
public final class NamespaceNames {

    private static final String PREFIX = "townstead.pack.";

    private NamespaceNames() {}

    public static String translationKey(String namespace) {
        return PREFIX + namespace;
    }

    /** Returns the pack-authored, localized name, or empty when none was supplied. */
    public static Optional<String> authored(String namespace) {
        if (namespace == null || namespace.isBlank()) return Optional.empty();
        String key = translationKey(namespace);
        if (!I18n.exists(key)) return Optional.empty();
        String name = I18n.get(key).trim();
        return name.isEmpty() || name.equals(key) ? Optional.empty() : Optional.of(name);
    }
}
