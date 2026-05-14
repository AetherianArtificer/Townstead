package com.aetherianartificer.townstead.reaction;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-villager last-fired bookkeeping, keyed by an arbitrary string
 * (typically the reaction id, or a composite reaction-id + binding-key
 * for per-binding cooldowns). Operations split into {@link #canClaim}
 * and {@link #claim} so the dispatcher can peek both reaction-level and
 * binding-level cooldowns before committing the fire and stamping both.
 */
public final class ReactionCooldownTracker {
    private static final WeakHashMap<LivingEntity, Object2LongOpenHashMap<String>> BY_ENTITY = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private ReactionCooldownTracker() {}

    /**
     * Peek whether {@code cooldownTicks} have elapsed since the last
     * {@link #claim} on this key. {@code cooldownTicks == 0} always
     * returns true.
     */
    public static boolean canClaim(LivingEntity entity, String key, int cooldownTicks, long gameTime) {
        if (entity == null || key == null) return false;
        if (cooldownTicks <= 0) return true;
        synchronized (LOCK) {
            Object2LongOpenHashMap<String> map = BY_ENTITY.get(entity);
            if (map == null) return true;
            map.defaultReturnValue(Long.MIN_VALUE);
            long last = map.getLong(key);
            return last == Long.MIN_VALUE || gameTime - last >= cooldownTicks;
        }
    }

    /** Stamp this key as fired at {@code gameTime}. */
    public static void claim(LivingEntity entity, String key, long gameTime) {
        if (entity == null || key == null) return;
        synchronized (LOCK) {
            Object2LongOpenHashMap<String> map = BY_ENTITY.computeIfAbsent(entity, e -> new Object2LongOpenHashMap<>(4));
            map.put(key, gameTime);
        }
    }

    public static void forget(UUID ignored) {
        // Reserved hook for future explicit eviction. WeakHashMap handles the common case.
    }
}
