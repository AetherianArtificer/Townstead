package com.aetherianartificer.townstead.client.animation.emote.loader;

import com.aetherianartificer.townstead.client.animation.emote.EmoteRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Client-side index from Emotecraft animation UUID to the
 * Townstead-canonical emote name (the file-stem, e.g. {@code waving}).
 * Populated by {@link EmotecraftEmoteLoader} every time {@link EmoteRegistry#reload()}
 * runs and walks the resource pack for emote files.
 *
 * <p>Used by {@code EmotecraftEventBridge} when the local player plays
 * an emote via Emotecraft's B menu: the firing event carries the
 * {@code KeyframeAnimation}'s UUID, this index resolves it to a name
 * the reaction system can broadcast as a gesture.</p>
 *
 * <p>Fully data-driven: any emote file the resource pack stack contains
 * (Emotecraft's built-ins under {@code assets/emotecraft/emotes/} or
 * Townstead-shipped emotes under {@code assets/<ns>/townstead_emotes/})
 * is picked up automatically. User-loaded emotes from a personal
 * {@code emotes/} folder live outside the resource pack stack and aren't
 * indexed; they can still play but won't broadcast a gesture.</p>
 */
public final class EmoteNameIndex {
    private static final Map<UUID, String> BY_UUID = new HashMap<>();
    private static final Object LOCK = new Object();

    private EmoteNameIndex() {}

    public static void clear() {
        synchronized (LOCK) {
            BY_UUID.clear();
        }
    }

    public static void register(UUID uuid, ResourceLocation emoteId) {
        if (uuid == null || emoteId == null) return;
        String name = emoteId.getPath();
        if (name == null || name.isBlank()) return;
        synchronized (LOCK) {
            BY_UUID.put(uuid, name);
        }
    }

    public static Optional<String> nameFor(UUID uuid) {
        if (uuid == null) return Optional.empty();
        synchronized (LOCK) {
            return Optional.ofNullable(BY_UUID.get(uuid));
        }
    }
}
