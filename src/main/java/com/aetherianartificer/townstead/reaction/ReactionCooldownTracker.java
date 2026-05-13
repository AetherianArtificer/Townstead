package com.aetherianartificer.townstead.reaction;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Per-villager, per-reaction last-fired bookkeeping. Keyed weakly by
 * entity so unloaded villagers drop out without manual cleanup. Reactions
 * are keyed by {@link ResourceLocation} (string interning makes the
 * Object2LongOpenHashMap effectively id-pointer keyed).
 */
public final class ReactionCooldownTracker {
    private static final WeakHashMap<LivingEntity, Object2LongOpenHashMap<ResourceLocation>> BY_ENTITY =
            new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private ReactionCooldownTracker() {}

    /**
     * Attempt to claim a fire slot. Returns {@code true} when the reaction
     * is off cooldown for this villager and records the fire timestamp;
     * returns {@code false} when still cooling down (no state change).
     * Cooldown {@code 0} always claims successfully and updates the stamp.
     */
    public static boolean tryClaim(LivingEntity entity, ResourceLocation reactionId, int cooldownTicks, long gameTime) {
        if (entity == null || reactionId == null) return false;
        synchronized (LOCK) {
            Object2LongOpenHashMap<ResourceLocation> map =
                    BY_ENTITY.computeIfAbsent(entity, e -> new Object2LongOpenHashMap<>(4));
            map.defaultReturnValue(Long.MIN_VALUE);
            long last = map.getLong(reactionId);
            if (cooldownTicks > 0 && last != Long.MIN_VALUE && gameTime - last < cooldownTicks) {
                return false;
            }
            map.put(reactionId, gameTime);
            return true;
        }
    }

    public static void forget(UUID ignored) {
        // Reserved hook for future explicit eviction. WeakHashMap handles the common case.
    }
}
