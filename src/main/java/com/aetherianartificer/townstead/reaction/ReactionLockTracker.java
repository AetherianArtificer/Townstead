package com.aetherianartificer.townstead.reaction;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Tracks per-villager "currently playing a locking reaction" stamps. While
 * locked, the dispatcher refuses to pick another reaction (except for the
 * {@code COMMAND} source) and {@link #tickFreeze} keeps the villager
 * stationary every tick so the Emotecraft adapter doesn't fade the
 * animation out on detected limb movement.
 *
 * <p>The walk target the villager had at lock time is captured and
 * restored when the lock expires, so an interrupted task can resume
 * rather than the brain wandering off during the freeze.</p>
 */
public final class ReactionLockTracker {
    private static final class State {
        long until;
        ResourceLocation reactionId;
        WalkTarget savedWalkTarget;
        boolean awaitingRestore;
    }

    private static final WeakHashMap<LivingEntity, State> LOCKED = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private ReactionLockTracker() {}

    public static boolean isLocked(LivingEntity entity, long gameTime) {
        if (entity == null) return false;
        synchronized (LOCK) {
            State s = LOCKED.get(entity);
            return s != null && gameTime < s.until;
        }
    }

    public static ResourceLocation activeReaction(LivingEntity entity, long gameTime) {
        if (entity == null) return null;
        synchronized (LOCK) {
            State s = LOCKED.get(entity);
            return (s != null && gameTime < s.until) ? s.reactionId : null;
        }
    }

    public static void lock(LivingEntity entity, long gameTime, int lockTicks, ResourceLocation reactionId) {
        if (entity == null || lockTicks <= 0) return;
        synchronized (LOCK) {
            State s = LOCKED.computeIfAbsent(entity, e -> new State());
            s.until = gameTime + lockTicks;
            s.reactionId = reactionId;
            s.savedWalkTarget = captureWalkTarget(entity);
            s.awaitingRestore = true;
        }
        freeze(entity);
    }

    /**
     * Called every tick on every MCA villager. Three responsibilities:
     * <ul>
     *   <li>While locked: re-cancel pathing + clear the walk target so
     *       the brain can't restart movement.
     *   <li>On the tick the lock expires: restore the saved walk target
     *       (if the brain hasn't set a fresh one) so the villager picks
     *       their task back up.
     *   <li>Once restored: drop the entry entirely.
     * </ul>
     */
    public static void tickFreeze(VillagerEntityMCA villager, long gameTime) {
        if (villager == null) return;
        State s;
        synchronized (LOCK) {
            s = LOCKED.get(villager);
        }
        if (s == null) return;
        if (gameTime < s.until) {
            freeze(villager);
        } else if (s.awaitingRestore) {
            restoreWalkTarget(villager, s.savedWalkTarget);
            synchronized (LOCK) {
                LOCKED.remove(villager);
            }
        }
    }

    private static WalkTarget captureWalkTarget(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA mca)) return null;
        try {
            Optional<WalkTarget> current = mca.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            return current.orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void restoreWalkTarget(VillagerEntityMCA villager, WalkTarget saved) {
        if (saved == null) return;
        try {
            // Only restore when the brain hasn't already chosen something
            // new; if it has, leave the new choice alone.
            if (villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty()) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, saved);
            }
        } catch (Throwable ignored) {}
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
        var v = entity.getDeltaMovement();
        entity.setDeltaMovement(0.0, v.y, 0.0);
    }
}
