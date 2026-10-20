package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.root.needs.NeedSuppression;
import net.minecraft.server.level.ServerPlayer;

/**
 * A player whose genes switch thirst off ({@code liquid:"none"}) keeps the active thirst mod's
 * bar full, the player twin of the villager pin in {@code ThirstVillagerTicker}. Townstead does
 * not own the player bar, so it refills it through the bridge's own drink entry: the bar and
 * the reserve behind it are topped up whenever the bar dips, and the reserve on a slower beat
 * so the bar itself never starts to drain.
 */
public final class PlayerThirstSuppression {
    private static final int CHECK_INTERVAL = 20;
    private static final int RESERVE_INTERVAL = 200;
    private static final int FULL = 20;

    private PlayerThirstSuppression() {}

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL != 0) return;
        if (player.isCreative() || player.isSpectator()) return;
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !NeedSuppression.suppressesThirst(player)) return;
        double thirst = bridge.playerThirst(player);
        boolean low = !Double.isNaN(thirst) && thirst < FULL;
        if (low || player.tickCount % RESERVE_INTERVAL == 0) bridge.restorePlayerThirst(player, FULL, FULL);
    }
}
