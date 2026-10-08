package com.aetherianartificer.townstead.client.gui.storage;

import com.aetherianartificer.townstead.storage.net.RoomOwnershipSnapshotS2CPayload;
import net.minecraft.client.Minecraft;

/** Keeps client classes out of the common room-tag interaction path. */
public final class RoomOwnershipScreenOpener {
    private RoomOwnershipScreenOpener() {}

    public static void open(RoomOwnershipSnapshotS2CPayload snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.setScreen(new RoomOwnershipScreen(snapshot));
    }
}
