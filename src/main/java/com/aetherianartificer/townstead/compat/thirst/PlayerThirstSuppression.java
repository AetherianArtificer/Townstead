package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A player whose genes switch thirst off ({@code liquid:"none"}) keeps the active thirst mod's
 * bar full, the player twin of the villager pin in {@code ThirstVillagerTicker}. Townstead does
 * not own the player bar, so it refills it through the bridge's own drink entry: the bar and
 * the reserve behind it are topped up whenever the bar dips, and the reserve on a slower beat
 * so the bar itself never starts to drain.
 *
 * <p>A hydration {@code rate} below 1 gives back the rest of each point the bar loses, carried
 * over until it makes a whole point, so the bar falls at that share of the mod's own pace.</p>
 */
public final class PlayerThirstSuppression {
    private static final int CHECK_INTERVAL = 20;
    private static final int RESERVE_INTERVAL = 200;
    private static final int FULL = 20;

    private record Partial(double last, double owed) {}
    private static final Map<ServerPlayer, Partial> PARTIAL = new WeakHashMap<>();

    private PlayerThirstSuppression() {}

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (player.isCreative() || player.isSpectator()) {
            PARTIAL.remove(player);
            return;
        }
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) return;
        if (NeedSuppression.suppressesThirst(player)) {
            PARTIAL.remove(player);
            double thirst = bridge.playerThirst(player);
            boolean low = !Double.isNaN(thirst) && thirst < FULL;
            if (low || player.tickCount % RESERVE_INTERVAL == 0) bridge.restorePlayerThirst(player, FULL, FULL);
            return;
        }
        float rate = NeedSuppression.thirstRate(player);
        double thirst = rate < 1f ? bridge.playerThirst(player) : Double.NaN;
        if (Double.isNaN(thirst)) {
            PARTIAL.remove(player);
            return;
        }
        Partial previous = PARTIAL.get(player);
        double owed = previous == null ? 0 : owed(previous.last(), thirst, rate, previous.owed());
        int refund = (int) Math.floor(owed);
        if (refund > 0 && bridge.restorePlayerThirst(player, refund, 0)) {
            owed -= refund;
            double after = bridge.playerThirst(player);
            if (!Double.isNaN(after)) thirst = after;
        }
        PARTIAL.put(player, new Partial(thirst, owed));
    }

    /** What is owed back after the bar moved from {@code last} to {@code now}: the unfelt share of any loss. */
    static double owed(double last, double now, float rate, double owed) {
        return now < last ? owed + (last - now) * (1 - rate) : owed;
    }
}
