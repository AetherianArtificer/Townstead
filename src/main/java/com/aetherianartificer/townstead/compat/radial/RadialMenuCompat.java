package com.aetherianartificer.townstead.compat.radial;

import com.aetherianartificer.townstead.compat.ModCompat;

/**
 * Third-party radial menus, which can fire a Townstead ability without any key being bound.
 *
 * <p>PRESENCE ONLY, deliberately. A radial menu of this kind stores one fact about us — the NAME of
 * a keybind — and presses it. It has no field that could hold an ability, so there is nothing to
 * import and no assignment to read back: which ability sits in a slot is Townstead's state and
 * always will be. Anything more than "is it installed" would be inventing a coupling that the other
 * mod's data model cannot support.</p>
 *
 * <p>What the answer IS good for is telling the truth. Ability keys default to unbound, and the
 * record panel warns when one is, on the grounds that an unbound ability does nothing. For a player
 * driving abilities from a wheel that warning is simply wrong, and a red warning aimed at somebody
 * with no problem is worse than no warning at all.</p>
 *
 * <p>No classes from these mods are referenced, so this stays safe on a dedicated server and cannot
 * break when they update. Compare the Emotecraft and Timekeeper bridges, which shadow types and do
 * break; a keybind is a vanilla contract and costs us nothing to honour.</p>
 */
public final class RadialMenuCompat {

    private RadialMenuCompat() {}

    /** Radial menus known to drive plain {@code KeyMapping}s, which is all ours require. */
    private static final String[] MOD_IDS = {"minemenu"};

    public static boolean anyLoaded() {
        for (String modId : MOD_IDS) {
            if (ModCompat.isLoaded(modId)) return true;
        }
        return false;
    }
}
