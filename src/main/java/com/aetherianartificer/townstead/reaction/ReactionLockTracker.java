package com.aetherianartificer.townstead.reaction;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.WeakHashMap;

/**
 * Tracks per-villager "currently playing a locking reaction" stamps. While
 * locked, the dispatcher refuses to pick another reaction (except for the
 * {@code COMMAND} source, which always bypasses lock to keep iteration
 * tight) and {@link #tickFreeze} keeps the villager stationary every tick
 * so the Emotecraft adapter doesn't fade the animation out on detected
 * limb movement. Stamps expire passively when {@code gameTime} crosses them.
 *
 * <p>Also remembers the reaction id whose lock is currently active, so
 * {@link ContextResolver}-style proximity tags can ask
 * "is anyone nearby mid-dance?".</p>
 */
public final class ReactionLockTracker {
    private record Lock(long until, ResourceLocation reactionId) {}

    private static final WeakHashMap<LivingEntity, Lock> LOCKED = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private ReactionLockTracker() {}

    public static boolean isLocked(LivingEntity entity, long gameTime) {
        if (entity == null) return false;
        synchronized (LOCK) {
            Lock l = LOCKED.get(entity);
            return l != null && gameTime < l.until;
        }
    }

    /**
     * Reaction id currently locking this villager, or {@code null} when
     * not locked or the lock has expired. Used by activity-proximity tags.
     */
    public static ResourceLocation activeReaction(LivingEntity entity, long gameTime) {
        if (entity == null) return null;
        synchronized (LOCK) {
            Lock l = LOCKED.get(entity);
            return (l != null && gameTime < l.until) ? l.reactionId : null;
        }
    }

    public static void lock(LivingEntity entity, long gameTime, int lockTicks, ResourceLocation reactionId) {
        if (entity == null || lockTicks <= 0) return;
        synchronized (LOCK) {
            LOCKED.put(entity, new Lock(gameTime + lockTicks, reactionId));
        }
        freeze(entity);
    }

    /**
     * Called every tick on every MCA villager (cheap when not locked).
     * While the lock holds, repeatedly cancels pathing and clears the
     * walk target so the brain can't restart movement and the Emotecraft
     * adapter sees {@code limbDistance} drop below its cancel threshold.
     */
    public static void tickFreeze(VillagerEntityMCA villager, long gameTime) {
        if (villager == null) return;
        if (!isLocked(villager, gameTime)) return;
        freeze(villager);
    }

    private static void freeze(LivingEntity entity) {
        if (entity instanceof Mob mob) {
            try {
                mob.getNavigation().stop();
            } catch (Throwable ignored) {}
        }
        if (entity instanceof VillagerEntityMCA mca) {
            try {
                mca.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } catch (Throwable ignored) {}
        }
        // Zero horizontal velocity so prior momentum doesn't keep them
        // drifting into the limb-distance threshold for an extra few ticks.
        var v = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0, v.y, 0.0);
    }
}
