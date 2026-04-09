package com.aetherianartificer.townstead.client.gui.fieldpost;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Client-only helper to open the Field Post screen.
 * Called via reflection from FieldPostBlock to avoid client class references on servers.
 */
public final class FieldPostScreenOpener {
    private FieldPostScreenOpener() {}

    public static void open(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.setScreen(new FieldPostScreen(pos, mc.level));
    }
}
