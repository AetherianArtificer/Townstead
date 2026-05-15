package com.aetherianartificer.townstead.reaction.trigger.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Server-side registry of "this villager is currently in dialogue with
 * that player" plus a short trailing window after the dialogue closes.
 * Populated by a small client C2S packet sent from
 * {@code RpgDialogueScreen} on open and close.
 *
 * <p>Used by {@link ContextResolver} to emit
 * {@code in_dialogue_with_player} and {@code dialogue_just_ended} so
 * pack authors can wire reactions to dialogue boundaries.</p>
 */
public final class DialogueStateTracker {
    private static final int RECENT_END_WINDOW_TICKS = 60;

    private record State(UUID playerUuid, long openSinceTick, long closedAtTick) {
        boolean isOpen() {
            return closedAtTick < 0L;
        }
    }

    private static final WeakHashMap<LivingEntity, State> BY_VILLAGER = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private DialogueStateTracker() {}

    public static void onOpen(LivingEntity villager, UUID playerUuid, long gameTime) {
        if (villager == null || playerUuid == null) return;
        synchronized (LOCK) {
            BY_VILLAGER.put(villager, new State(playerUuid, gameTime, -1L));
        }
    }

    public static void onClose(LivingEntity villager, long gameTime) {
        if (villager == null) return;
        synchronized (LOCK) {
            State existing = BY_VILLAGER.get(villager);
            if (existing == null || !existing.isOpen()) return;
            BY_VILLAGER.put(villager, new State(existing.playerUuid, existing.openSinceTick, gameTime));
        }
    }

    /** Active dialogue partner UUID, or {@code null} when not in dialogue. */
    public static UUID activePartner(LivingEntity villager) {
        if (villager == null) return null;
        synchronized (LOCK) {
            State s = BY_VILLAGER.get(villager);
            return (s != null && s.isOpen()) ? s.playerUuid : null;
        }
    }

    /** True if dialogue closed within the last {@link #RECENT_END_WINDOW_TICKS}. */
    public static boolean dialogueJustEnded(LivingEntity villager, long gameTime) {
        if (villager == null) return false;
        synchronized (LOCK) {
            State s = BY_VILLAGER.get(villager);
            if (s == null || s.isOpen()) return false;
            return gameTime - s.closedAtTick <= RECENT_END_WINDOW_TICKS;
        }
    }

    /**
     * Drop stale closed-state entries so they don't accumulate. Called
     * opportunistically; the {@link WeakHashMap} also reclaims entries
     * when villagers unload.
     */
    public static void prune(ServerLevel level) {
        long now = level.getGameTime();
        synchronized (LOCK) {
            BY_VILLAGER.entrySet().removeIf(e -> {
                State s = e.getValue();
                return !s.isOpen() && now - s.closedAtTick > RECENT_END_WINDOW_TICKS * 4L;
            });
        }
    }
}
