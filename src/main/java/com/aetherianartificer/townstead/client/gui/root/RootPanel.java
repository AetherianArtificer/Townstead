package com.aetherianartificer.townstead.client.gui.root;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A vanilla-style translucent panel, drawn with fills so it renders identically
 * on 1.20.1 and 1.21 and sits over the world like Minecraft's own in-world menus
 * (the editor is a transparent in-world screen). Used behind the origin list and
 * detail pane so they match the rest of the Destiny/editor UI.
 */
public final class RootPanel {

    private RootPanel() {}

    public static void draw(GuiGraphics g, int x0, int y0, int x1, int y1) {
        // Translucent dark body (world shows through faintly, like vanilla lists).
        g.fill(x0, y0, x1, y1, 0xC8101010);
        drawBorder(g, x0, y0, x1, y1);
    }

    /**
     * The beveled frame only (no body fill). Drawn separately so a scrollable list can
     * stamp it back on top of its rows, where vanilla's edge-fade gradient and selected-row
     * fills would otherwise paint over the top border line.
     */
    static void drawBorder(GuiGraphics g, int x0, int y0, int x1, int y1) {
        // Soft beveled edge: light top/left, dark bottom/right, like vanilla widgets.
        drawTopEdge(g, x0, y0, x1);
        g.fill(x0, y0, x0 + 1, y1, 0x40FFFFFF);
        g.fill(x0, y1 - 1, x1, y1, 0x40000000);
        g.fill(x1 - 1, y0, x1, y1, 0x40000000);
    }

    /**
     * Just the top edge of the bevel. A scrollable list stamps only this back over its rows
     * (vanilla's top edge-fade gradient otherwise swallows it), leaving the side and bottom
     * edges from the background draw alone, so a selected row's white outline isn't covered
     * by the full-height dark right bevel.
     */
    static void drawTopEdge(GuiGraphics g, int x0, int y0, int x1) {
        g.fill(x0, y0, x1, y0 + 1, 0x40FFFFFF);
    }
}
