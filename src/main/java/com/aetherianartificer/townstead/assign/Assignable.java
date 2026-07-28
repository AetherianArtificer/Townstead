package com.aetherianartificer.townstead.assign;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Anything a player can put on a wheel slot, whoever it came from.
 *
 * <p>The seam that lets this stop being "a Townstead ability". A slot stores an {@link #id} and
 * nothing else; what that id MEANS is answered by whichever provider claims it. Townstead's own
 * abilities are one provider, a datapack's actions are another, and a mod bridge would be a third.
 * Ours deliberately has no shortcut: if the built-in path used a back door, every other source would
 * get the second-class version of this and nobody would notice until someone tried to use it.</p>
 */
public record Assignable(
        ResourceLocation id,
        Component name,
        /** An item to draw, or empty to fall back to the name's initials. */
        String icon,
        /** The heading this groups under. A source is just a string, so a new one needs no code. */
        Component source,
        Kind kind,
        /** Ticks, or 0 when there is none, or -1 when the owner will not say. */
        int cooldownTicks,
        int costAmount,
        String costLabel,
        /** The resource's authored colour, or 0 when it declares none. */
        int costColor,
        /**
         * What the CLIENT needs to perform this, for the kinds the client performs.
         *
         * <p>A keybind's translation key, and nothing at all for a command: the server runs those,
         * so shipping the command text would tell every client what it could try to forge.</p>
         */
        String clientValue) {

    /**
     * What pressing it does.
     *
     * <p>Four is enough, which MineMenu's own action list is decent evidence for: nearly every mod
     * exposes a command, a keybind or an item, and the ones that do not are the ones worth writing a
     * real bridge for.</p>
     */
    public enum Kind {
        /** Ours: a gene or skill power, fired through the ability layer. */
        ABILITY,
        /** A server command, run as the server and gated by the entry's own requirements. */
        COMMAND,
        /** A client keybind pressed on the player's behalf. */
        KEYBIND,
        /** Use an item the player is holding or carrying. */
        ITEM;

        public boolean isToggle() {
            return false;
        }
    }

    /** A cooldown the owner declined to report, which the wheel must not draw a full ring for. */
    public static final int COOLDOWN_UNKNOWN = -1;
}
