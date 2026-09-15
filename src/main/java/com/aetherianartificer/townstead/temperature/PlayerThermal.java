package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import net.minecraft.world.entity.player.Player;

/**
 * Runs the expiry for a player's thermal influence. Only backends that hold their own copy of the
 * value do any work here; the rest let their own temperature decay and ignore the call.
 *
 * <p>Throttled because an influence measured in seconds does not need tick-exact expiry, and this
 * runs for every player every tick.</p>
 */
public final class PlayerThermal {

    private static final int INTERVAL = 10;

    private PlayerThermal() {}

    public static void tick(Player player) {
        if (player == null || player.level().isClientSide) return;
        if ((player.level().getGameTime() + player.getId()) % INTERVAL != 0) return;
        if (!PlayerThermalOffsets.expireIfDue(player)) return;
        AmbientTemperatureBridge bridge = TemperatureBridgeResolver.get();
        if (bridge != null) bridge.onThermalInfluenceEnded(player);
    }
}
