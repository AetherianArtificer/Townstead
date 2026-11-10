package com.aetherianartificer.townstead.client.naming;

import com.aetherianartificer.townstead.naming.NameClientStore;
import com.aetherianartificer.townstead.naming.NameSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/** Applies a name sync. A player's display name is cached, so it is rebuilt from the new family name. */
public final class NameSyncClient {

    private static int watched = -1;
    private static Runnable onWatched;

    private NameSyncClient() {}

    /** Runs {@code onSync} whenever this entity's name is synced, for a screen showing it. */
    public static void watch(int entityId, Runnable onSync) {
        watched = entityId;
        onWatched = onSync;
    }

    public static void unwatch() {
        watched = -1;
        onWatched = null;
    }

    public static void apply(NameSyncPayload payload) {
        NameClientStore.set(payload.entityId(), payload.familyName(), payload.culture(),
                payload.order(), payload.familyType(), payload.tradition(), payload.familyLocked());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getEntity(payload.entityId()) instanceof Player player) {
            player.refreshDisplayName();
        }
        if (payload.entityId() == watched && onWatched != null) onWatched.run();
    }
}
