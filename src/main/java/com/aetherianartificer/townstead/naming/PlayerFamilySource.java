package com.aetherianartificer.townstead.naming;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Another mod's say over a player's family name. Registered through
 * {@link PlayerNames#addSource(PlayerFamilySource)} at setup, gated on that mod.
 */
public interface PlayerFamilySource {

    /** A name this mod holds as settled, which outranks anything typed or rolled. Empty when none. */
    default String authoritative(Player player) {
        return "";
    }

    /** A name for a tradition that builds none of its own. Empty when this mod has none to offer. */
    default String fallback(Player player, @Nullable NamingTradition tradition) {
        return "";
    }
}
