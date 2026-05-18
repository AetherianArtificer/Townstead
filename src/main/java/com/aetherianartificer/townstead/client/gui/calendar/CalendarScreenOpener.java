package com.aetherianartificer.townstead.client.gui.calendar;

import net.minecraft.client.Minecraft;

/**
 * Client-only entry point for opening the calendar UI from a block-interaction
 * code path. Keeps the {@code net.minecraft.client} reference out of
 * {@link com.aetherianartificer.townstead.block.CalendarBlock}'s direct
 * imports so the block class stays safe to load on the dedicated server.
 *
 * Invoked from the block's {@code useWithoutItem} / {@code use} guarded by
 * {@code level.isClientSide()}.
 */
public final class CalendarScreenOpener {
    private CalendarScreenOpener() {}

    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(new CalendarScreen());
    }
}
