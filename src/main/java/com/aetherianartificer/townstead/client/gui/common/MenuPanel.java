package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A popover list: the panel a picker or a drawer opens as.
 *
 * <p>The career picker and the seal case had each grown their own version of this, close enough
 * that they read as the same control and different enough that they read as sloppy. The panel
 * grounds, row heights, selection ticks and scrollbars were all separately authored, and the
 * picker's colours were raw hex that never reached {@link Palette}.</p>
 *
 * <p>Callers keep their own row CONTENT, since one draws an item icon with a trailing rank and
 * the other a seal device. What lives here is everything around it: the frame, the row grounds,
 * the selection tick, the scrollbar, and the arithmetic that turns a mouse position into a row.</p>
 */
public final class MenuPanel {

    /** One row, and the heading band above them. Shared so two menus cannot drift apart. */
    public static final int ROW_H = 14;
    public static final int HEAD_H = 14;
    /** The inset a row keeps from the panel's own edge. */
    public static final int ROW_INSET = 2;
    /**
     * Where a row's own content sits, measured from the panel's left edge and the row's top.
     * Shared for the same reason the frame is: two menus whose leading glyph starts five pixels
     * apart do not read as one control however alike their colours are.
     */
    public static final int ICON_X = 6;
    public static final int ICON_W = 12;
    public static final int LABEL_X = 19;
    public static final int TEXT_Y = 3;

    private MenuPanel() {}

    public static int height(int visibleRows, boolean heading) {
        return (heading ? HEAD_H : ROW_INSET) + visibleRows * ROW_H + ROW_INSET;
    }

    public static int rowsTop(int y, boolean heading) {
        return y + (heading ? HEAD_H : ROW_INSET);
    }

    /** How many rows fit, given the room the caller can spare. */
    public static int fit(int available, boolean heading) {
        return Math.max(1, (available - (heading ? HEAD_H : ROW_INSET) - ROW_INSET) / ROW_H);
    }

    /** The row under the cursor, or -1. Local to the panel: add the scroll offset yourself. */
    public static int rowAt(double mouseX, double mouseY, int x, int y, int w,
                            boolean heading, int visibleRows) {
        if (mouseX < x || mouseX >= x + w) return -1;
        int top = rowsTop(y, heading);
        if (mouseY < top) return -1;
        int local = (int) ((mouseY - top) / ROW_H);
        return local < 0 || local >= visibleRows ? -1 : local;
    }

    /**
     * The panel itself.
     *
     * @param heading  the band's label, or null for a panel that opens straight onto its rows
     * @param seated   true when the panel meets furniture along its bottom edge, which then goes
     *                 undrawn so the two read as one object rather than a box resting on a box
     */
    public static void drawFrame(GuiGraphics g, Font font, int x, int y, int w, int h,
                                 String heading, boolean seated) {
        if (!seated) {
            g.fill(x + 2, y + 2, x + w + 2, y + h + 2, Palette.MENU_SHADOW);
        }
        g.fill(x, y, x + w, y + h, Palette.MENU_GROUND);
        g.fill(x, y, x + w, y + 1, Palette.MENU_EDGE);
        g.fill(x, y, x + 1, y + h, Palette.MENU_EDGE);
        g.fill(x + w - 1, y, x + w, y + h, Palette.MENU_EDGE);
        if (!seated) g.fill(x, y + h - 1, x + w, y + h, Palette.MENU_EDGE);
        if (heading == null || heading.isEmpty()) return;
        g.drawString(font, heading, x + 5, y + 3, Palette.MENU_HEADING, false);
        g.fill(x + 4, y + HEAD_H - 2, x + w - 4, y + HEAD_H - 1, Palette.MENU_RULE);
    }

    /** A row's ground and its selection tick. Content is the caller's business. */
    public static void drawRow(GuiGraphics g, int x, int y, int w, boolean selected,
                               boolean hover) {
        if (selected || hover) {
            g.fill(x + ROW_INSET, y, x + w - ROW_INSET, y + ROW_H - 1,
                    selected ? Palette.MENU_ROW_ON : Palette.MENU_ROW_HOVER);
        }
        if (selected) g.fill(x + ROW_INSET, y, x + ROW_INSET + 2, y + ROW_H - 1, Palette.BRASS);
    }

    /** Only draws when there is more content than room. */
    public static void drawScrollbar(GuiGraphics g, int x, int y, int w, int h,
                                     boolean heading, int first, int visibleRows, int total) {
        int max = Math.max(0, total - visibleRows);
        if (max <= 0) return;
        int trackY = rowsTop(y, heading);
        int trackH = Math.max(1, y + h - trackY - ROW_INSET);
        int thumbH = Math.max(8, trackH * visibleRows / total);
        int thumbY = trackY + (trackH - thumbH) * first / max;
        g.fill(x + w - 4, trackY, x + w - 2, trackY + trackH, Palette.MENU_TRACK);
        g.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumbH, Palette.BRASS_DEEP);
        g.fill(x + w - 4, thumbY, x + w - 3, thumbY + thumbH, Palette.BRASS);
    }

    /** Text colours a row's own content should use, so two menus never disagree about them. */
    public static int labelColor(boolean selected) {
        return selected ? Palette.LABEL_LIGHT : Palette.LABEL_MID;
    }

    public static int trailingColor() {
        return Palette.MENU_HEADING;
    }
}
