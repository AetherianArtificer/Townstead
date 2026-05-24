package com.aetherianartificer.townstead.client.gui.origin;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A vanilla-style translucent panel, drawn with fills so it renders identically
 * on 1.20.1 and 1.21 and sits over the world like Minecraft's own in-world menus
 * (the editor is a transparent in-world screen). Used behind the origin list and
 * detail pane so they match the rest of the Destiny/editor UI.
 */
final class OriginPanel {

    private OriginPanel() {}

    static void draw(GuiGraphics g, int x0, int y0, int x1, int y1) {
        // Translucent dark body (world shows through faintly, like vanilla lists).
        g.fill(x0, y0, x1, y1, 0xC8101010);
        // Soft beveled edge: light top/left, dark bottom/right, like vanilla widgets.
        g.fill(x0, y0, x1, y0 + 1, 0x40FFFFFF);
        g.fill(x0, y0, x0 + 1, y1, 0x40FFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x40000000);
        g.fill(x1 - 1, y0, x1, y1, 0x40000000);
    }
}
