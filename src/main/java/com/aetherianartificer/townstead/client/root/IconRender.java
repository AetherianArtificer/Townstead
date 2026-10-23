package com.aetherianartificer.townstead.client.root;

import java.util.Set;

/**
 * While a root icon renders ({@link RootIcons}), bodies draw only their head: the body, arms and
 * legs (and everything anchored to them, a tail, wings) are hidden on MCA's model and on rigs alike.
 */
public final class IconRender {
    /** The model groups hidden for a head-only render. */
    public static final Set<String> BELOW_THE_NECK = Set.of("body", "arms", "legs");

    private static boolean headOnly;

    private IconRender() {}

    public static boolean headOnly() {
        return headOnly;
    }

    static void setHeadOnly(boolean on) {
        headOnly = on;
    }
}
