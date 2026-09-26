package com.aetherianartificer.townstead.client.haze;

import com.aetherianartificer.townstead.block.haze.HazeKinds;
import com.aetherianartificer.townstead.block.haze.HazeKindsSyncPayload;
import net.minecraft.client.Minecraft;

/** Applies the server's haze kinds and re-meshes chunks, since a cell's look depends on them. */
public final class HazeClient {

    private HazeClient() {}

    public static void apply(HazeKindsSyncPayload payload) {
        HazeKinds.client().replaceAll(payload.kinds());
        HazeModel.invalidate();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) minecraft.levelRenderer.allChanged();
    }
}
