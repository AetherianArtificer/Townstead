package com.aetherianartificer.townstead.compat.werewolves;

import com.aetherianartificer.townstead.compat.ModCompat;

/**
 * Werewolves tells a player on joining that it does not work with MCA, once, then marks its own
 * client config so it never says it again. Townstead fixes that clash (see
 * {@code ForeignPlayerRendererBodyMixin}), so before Werewolves gets to it, its config is marked as
 * already told. Its own setting, written the way its handler writes it; nothing of the mod changes.
 */
public final class WerewolvesMcaNotice {
    private static boolean done;

    private WerewolvesMcaNotice() {}

    public static void silence() {
        if (done || !ModCompat.isLoaded("werewolves") || !ModCompat.isLoaded("mca")) return;
        done = true;
        try {
            Object client = Class.forName("de.teamlapen.werewolves.config.WerewolvesConfig").getField("CLIENT").get(null);
            Object flag = client.getClass().getField("mcaMessage").get(client);
            if (Boolean.TRUE.equals(flag.getClass().getMethod("get").invoke(flag))) return;
            flag.getClass().getMethod("set", Object.class).invoke(flag, Boolean.TRUE);
        } catch (Throwable ignored) {
            // A Werewolves without the notice; nothing to silence.
        }
    }
}
