package com.aetherianartificer.townstead.client.animation.nativeclip;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Active native clips, independently arbitrated per semantic channel. */
public final class NativePlaybackRegistry {
    public record Playback(ResourceLocation clip, long startedAt, long expiresAt, int priority, float speed) {
        public Playback(ResourceLocation clip, long startedAt, long expiresAt, int priority) {
            this(clip, startedAt, expiresAt, priority, 1F);
        }
        public float elapsed(long now, float partial) { return (now - startedAt + partial) * speed; }
    }
    private record Key(int entityId, String channel) {}
    private static final ConcurrentHashMap<Key, Playback> ACTIVE = new ConcurrentHashMap<>();
    private NativePlaybackRegistry() {}

    public static void put(int entityId, String channel, Playback playback) { ACTIVE.put(new Key(entityId, channel), playback); }
    public static void remove(int entityId, String channel) { ACTIVE.remove(new Key(entityId, channel)); }
    /** True while any native performance owns a channel for this entity. */
    public static boolean hasActive(int entityId, long now) {
        ACTIVE.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        return ACTIVE.keySet().stream().anyMatch(key -> key.entityId() == entityId);
    }
    public static Map<String, Playback> forEntity(int entityId, long now) {
        Map<String, Playback> out = new java.util.LinkedHashMap<>();
        ACTIVE.entrySet().removeIf(e -> e.getValue().expiresAt() <= now);
        ACTIVE.forEach((key, value) -> { if (key.entityId() == entityId) out.put(key.channel(), value); });
        return out;
    }
    public static void clear() { ACTIVE.clear(); }
}
