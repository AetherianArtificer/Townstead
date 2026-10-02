package com.aetherianartificer.townstead.temperature;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A temporary warming or cooling influence on a player, in Celsius, held by Townstead and
 * expiring on its own.
 *
 * <p>It exists because the temperature backends disagree about what a nudge is. Cold Sweat
 * stores a body temperature that decays, so it needs nothing from here. Tough As Nails derives
 * the player's level every evaluation and asks its registered modifiers what to do, so it reads
 * this table from inside that callback. Legendary Survival Overhaul keeps a keyed offset that
 * holds until something changes it, so it writes the value here and clears it on expiry. One
 * table, three idioms.</p>
 *
 * <p>Server-side and per session: a warming that outlives a logout is not worth persisting.</p>
 */
public final class PlayerThermalOffsets {

    private record Offset(float celsius, long expiresAtGameTime) {}

    private static final Map<UUID, Offset> OFFSETS = new ConcurrentHashMap<>();

    private PlayerThermalOffsets() {}

    /**
     * Replaces any influence on the player. Replacing rather than accumulating keeps a repeated
     * source (a skill re-firing every few seconds) from stacking into something absurd, and
     * matches how the backends' own keyed modifiers behave.
     */
    public static void set(Player player, float celsius, int durationTicks) {
        if (player == null) return;
        if (celsius == 0f || durationTicks <= 0) {
            OFFSETS.remove(player.getUUID());
            return;
        }
        OFFSETS.put(player.getUUID(),
                new Offset(celsius, player.level().getGameTime() + durationTicks));
    }

    /**
     * The live influence in Celsius, or zero once it has run out.
     *
     * <p>Reading never drops an expired entry: {@link #expireIfDue} is the only remover, and it
     * has to be able to see the entry in order to report that it ended. A backend holding its own
     * copy would otherwise never be told to clear it.</p>
     */
    public static float get(Player player) {
        if (player == null) return 0f;
        Offset offset = OFFSETS.get(player.getUUID());
        if (offset == null) return 0f;
        return player.level().getGameTime() >= offset.expiresAtGameTime() ? 0f : offset.celsius();
    }

    public static boolean has(Player player) {
        return get(player) != 0f;
    }

    /**
     * Drops an expired influence and reports whether this call was the one that dropped it, so a
     * backend holding its own copy of the value knows to go and clear it.
     */
    public static boolean expireIfDue(Player player) {
        if (player == null) return false;
        Offset offset = OFFSETS.get(player.getUUID());
        if (offset == null) return false;
        if (player.level().getGameTime() < offset.expiresAtGameTime()) return false;
        return OFFSETS.remove(player.getUUID(), offset);
    }

    public static void clear(Player player) {
        if (player != null) OFFSETS.remove(player.getUUID());
    }

    /** Server stop and world unload; the table is session state, not save state. */
    public static void clearAll() {
        OFFSETS.clear();
    }
}
