package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * Helpers for shipping {@link Component} text values across the network as
 * (translate key, fallback) pairs instead of pre-resolved strings, so each
 * client renders in its own locale.
 *
 * The portable cross-branch approach is necessary because
 * {@code FriendlyByteBuf.writeComponent} in 1.21.x NeoForge requires a
 * {@code RegistryFriendlyByteBuf}, and {@code Component.Serializer.toJson}
 * requires a {@code HolderLookup.Provider}. Plain string pairs work
 * identically on both branches.
 */
public final class ComponentSync {
    private ComponentSync() {}

    /**
     * Extract a (key, fallback) pair from a Component. Translatable components
     * yield their key and (optional) fallback; literal components yield
     * ({@code ""}, literal-text) so the client can render the literal.
     */
    public static String[] extract(Component c) {
        if (c == null) return new String[] { "", "" };
        if (c.getContents() instanceof TranslatableContents tc) {
            String key = tc.getKey();
            String fb = tc.getFallback();
            return new String[] { key != null ? key : "", fb != null ? fb : "" };
        }
        return new String[] { "", c.getString() };
    }

    /** Extract a pair whose fallback is resolved from data-pack locale sidecars. */
    public static String[] extract(Component c, String locale) {
        String[] pair = extract(c);
        pair[1] = com.aetherianartificer.townstead.data.DataPackLang.resolveFallback(
                pair[0], locale, pair[1]);
        return pair;
    }

    /**
     * Rebuild a Component from a (key, fallback) pair. Empty key returns a
     * literal of the fallback; empty fallback returns a translatable without
     * fallback; both empty returns {@link Component#empty()}.
     */
    public static Component reconstruct(String key, String fallback) {
        boolean hasKey = key != null && !key.isEmpty();
        boolean hasFb = fallback != null && !fallback.isEmpty();
        if (!hasKey && !hasFb) return Component.empty();
        if (!hasKey) return Component.literal(fallback);
        if (!hasFb) return Component.translatable(key);
        return Component.translatableWithFallback(key, fallback);
    }
}
